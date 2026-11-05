/*
 * DNF mailbox notification bridge. Runs only inside a supported game process.
 * It accepts fixed-format local requests and calls the game's own mail notifier.
 */
#define _GNU_SOURCE
#include <dirent.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/select.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <time.h>
#include <unistd.h>

#define MAIL_DIR_DEFAULT "/data/channel-manager/mail-notify"
#define MAX_PATH_SIZE 1024
#define REQUEST_ID_LENGTH 32

typedef int (*mail_notify_callback)(unsigned int group, unsigned int character);

static int request_name(const char *name) {
    int i;
    if (strlen(name) != REQUEST_ID_LENGTH + 4 || strcmp(name + REQUEST_ID_LENGTH, ".req") != 0) return 0;
    for (i = 0; i < REQUEST_ID_LENGTH; i++) {
        char c = name[i];
        if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return 0;
    }
    return 1;
}

static int parse_request(const char *data, unsigned int *group, unsigned int *character, unsigned long *expires) {
    unsigned long g = 0, c = 0, expiry = 0;
    const char *p = data;
    if (*p < '0' || *p > '9') return 0;
    while (*p >= '0' && *p <= '9') {
        g = g * 10 + (unsigned int)(*p++ - '0');
        if (g > 6) return 0;
    }
    if (*p++ != ' ' || *p < '0' || *p > '9') return 0;
    while (*p >= '0' && *p <= '9') {
        unsigned int digit = (unsigned int)(*p++ - '0');
        if (c > (2147483647UL - digit) / 10) return 0;
        c = c * 10 + digit;
    }
    if (*p++ != ' ' || *p < '0' || *p > '9') return 0;
    while (*p >= '0' && *p <= '9') {
        unsigned int digit = (unsigned int)(*p++ - '0');
        if (expiry > (4294967295UL - digit) / 10) return 0;
        expiry = expiry * 10 + digit;
    }
    if ((*p != '\n' && *p != '\0') || (*p == '\n' && p[1] != '\0') || g == 0 || c == 0 || expiry == 0) return 0;
    *group = (unsigned int)g;
    *character = (unsigned int)c;
    *expires = expiry;
    return 1;
}

/* Each request is claimed with rename; multiple channels cannot send it twice. */
static int process_requests(const char *directory, unsigned int local_group, mail_notify_callback notify) {
    DIR *dir = opendir(directory);
    struct dirent *entry;
    int processed = 0;
    if (!dir) return 0;
    while (processed < 32 && (entry = readdir(dir)) != NULL) {
        char request[MAX_PATH_SIZE], work[MAX_PATH_SIZE], done[MAX_PATH_SIZE], result_file[MAX_PATH_SIZE], content[64];
        unsigned int group, character;
        unsigned long expires;
        int fd, length, result;
        if (!request_name(entry->d_name)) continue;
        if (snprintf(request, sizeof(request), "%s/%s", directory, entry->d_name) >= (int)sizeof(request)) continue;
        if (snprintf(work, sizeof(work), "%s/%.*s.work.%ld", directory, REQUEST_ID_LENGTH, entry->d_name, (long)getpid()) >= (int)sizeof(work)) continue;
        if (snprintf(done, sizeof(done), "%s/%.*s.done", directory, REQUEST_ID_LENGTH, entry->d_name) >= (int)sizeof(done)) continue;
        if (snprintf(result_file, sizeof(result_file), "%s/%.*s.result.%ld", directory, REQUEST_ID_LENGTH, entry->d_name, (long)getpid()) >= (int)sizeof(result_file)) continue;
        fd = open(request, O_RDONLY | O_NOFOLLOW);
        if (fd < 0) continue;
        length = (int)read(fd, content, sizeof(content) - 1);
        close(fd);
        if (length <= 0 || length >= (int)sizeof(content) - 1) continue;
        content[length] = '\0';
        if (!parse_request(content, &group, &character, &expires) || group != local_group) continue;
        if (rename(request, work) != 0) continue;
        /* An acknowledgement is durable evidence that this ID was dispatched. */
        if (access(done, F_OK) == 0) { unlink(work); continue; }
        result = ((unsigned long)time(NULL) <= expires) ? notify(group, character) : -1;
        fd = open(result_file, O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW, 0644);
        if (fd >= 0) {
            const char *message = result < 0 ? "expired\n" : result ? "sent\n" : "not-ready\n";
            size_t size = strlen(message);
            ssize_t written = write(fd, message, size);
            close(fd);
            if (written == (ssize_t)size) rename(result_file, done);
            else unlink(result_file);
        }
        unlink(work);
        processed++;
    }
    closedir(dir);
    return processed;
}

#ifndef DNF_MAIL_TEST
/* These symbols are validated against the game executable before the bridge is loaded. */
#define MONITOR_MANAGER_ADDRESS 0x0940be28U
#define GET_MONITOR_PROXY_ADDRESS 0x0811208aU
#define NOTIFY_NEW_MAIL_ADDRESS 0x08471072U
#define SLEEP_SELECT_RETURN_ADDRESS 0x086b1b85U
#define MAIN_LOOP_SLEEP_RETURN_ADDRESS 0x0829fd09U

static pthread_t main_thread;
static int (*next_select)(int, fd_set *, fd_set *, fd_set *, struct timeval *);
static const char *mail_dir;
static unsigned int server_group;
static struct timeval last_check, last_ready;
static int initialized;

#ifndef DNF_MAIL_FAKE_GAME
#include "quest_actions.h"
#endif

static int interval_elapsed(const struct timeval *now, const struct timeval *last, long interval_us) {
    if (now->tv_sec < last->tv_sec) return 1;
    if (now->tv_sec == last->tv_sec) return now->tv_usec - last->tv_usec >= interval_us;
    if (now->tv_sec == last->tv_sec + 1) return 1000000L + now->tv_usec - last->tv_usec >= interval_us;
    return 1;
}

#ifdef DNF_MAIL_FAKE_GAME
static void *get_monitor_proxy(unsigned int group) {
    (void)group;
    return (void *)1;
}

static int notify_game(unsigned int group, unsigned int character) {
    const char *path = getenv("DNF_MAIL_FAKE_LOG");
    char line[64];
    int fd, length;
    if (!path) return 0;
    fd = open(path, O_WRONLY | O_CREAT | O_APPEND, 0600);
    if (fd < 0) return 0;
    length = snprintf(line, sizeof(line), "%u %u\n", group, character);
    if (length <= 0 || write(fd, line, (size_t)length) != length) { close(fd); return 0; }
    close(fd);
    return 1;
}
#else
/* The game is a fixed-address executable; keep these calls indirect from the shared object. */
static void *(* volatile game_get_monitor_proxy)(void *, int) = (void *(*)(void *, int))(uintptr_t)GET_MONITOR_PROXY_ADDRESS;
static void (* volatile game_notify_new_mail)(void *, unsigned int) = (void (*)(void *, unsigned int))(uintptr_t)NOTIFY_NEW_MAIL_ADDRESS;

static void *get_monitor_proxy(unsigned int group) {
    void *manager = *(void * volatile *)(uintptr_t)MONITOR_MANAGER_ADDRESS;
    void *proxy;
    if (!manager) return NULL;
    proxy = game_get_monitor_proxy(manager, (int)group);
    if (!proxy || *(const unsigned int *)((const char *)proxy + 4) == 0) return NULL;
    return proxy;
}

static int notify_game(unsigned int group, unsigned int character) {
    void *proxy = get_monitor_proxy(group);
    if (!proxy) return 0;
    game_notify_new_mail(proxy, character);
    return 1;
}
#endif

static void touch_ready(void) {
    char ready[MAX_PATH_SIZE];
    int fd;
    if (snprintf(ready, sizeof(ready), "%s/ready-%ld", mail_dir, (long)getpid()) >= (int)sizeof(ready)) return;
    fd = open(ready, O_WRONLY | O_CREAT | O_TRUNC | O_NOFOLLOW, 0644);
    if (fd >= 0) { write(fd, "ready\n", 6); close(fd); }
}

__attribute__((constructor)) static void init_mail_notify(void) {
    const char *enabled = getenv("DNF_MAIL_NOTIFY_SUPPORTED");
    const char *group = getenv("SERVER_GROUP");
    if (!enabled || strcmp(enabled, "1") != 0 || !group || strlen(group) != 1 || group[0] < '1' || group[0] > '6') return;
    mail_dir = getenv("DNF_MAIL_NOTIFY_DIR");
    if (!mail_dir || !*mail_dir) mail_dir = MAIL_DIR_DEFAULT;
    if (mail_dir[0] != '/' || strlen(mail_dir) > MAX_PATH_SIZE - 100) return;
    server_group = (unsigned int)(group[0] - '0');
    main_thread = pthread_self();
    mkdir(mail_dir, 0700);
    next_select = dlsym(RTLD_NEXT, "select");
    initialized = next_select != NULL;
}

/* Supported game builds preserve the sleep_ext frame; the build also preserves ours. */
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wframe-address"
int select(int nfds, fd_set *readfds, fd_set *writefds, fd_set *exceptfds, struct timeval *timeout) {
    if (!next_select) next_select = dlsym(RTLD_NEXT, "select");
    if (!next_select) { errno = ENOSYS; return -1; }
    if (initialized && nfds == 0 && !readfds && !writefds && !exceptfds && pthread_equal(pthread_self(), main_thread)
#if !defined(DNF_MAIL_FAKE_GAME) || defined(DNF_MAIL_TEST_CALLSITE)
            && (uintptr_t)__builtin_return_address(0) == SLEEP_SELECT_RETURN_ADDRESS
            && (uintptr_t)__builtin_return_address(1) == MAIN_LOOP_SLEEP_RETURN_ADDRESS
#endif
    ) {
        struct timeval now;
        if (gettimeofday(&now, NULL) == 0 && interval_elapsed(&now, &last_check, 200000)) {
            last_check = now;
            if (get_monitor_proxy(server_group)) {
                if (interval_elapsed(&now, &last_ready, 1000000)) { touch_ready(); last_ready = now; }
                process_requests(mail_dir, server_group, notify_game);
#ifndef DNF_MAIL_FAKE_GAME
                process_quest_requests();
#endif
            }
        }
    }
    return next_select(nfds, readfds, writefds, exceptfds, timeout);
}
#pragma GCC diagnostic pop
#endif
