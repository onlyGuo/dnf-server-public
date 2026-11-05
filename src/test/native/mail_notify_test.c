#define DNF_MAIL_TEST
#include "../../../deploy/integrated/online-mail/mail_notify.c"
#include <assert.h>
#include <sys/wait.h>

static char notification_log[MAX_PATH_SIZE];

static int notify_test(unsigned int group, unsigned int character) {
    char line[64];
    int fd = open(notification_log, O_WRONLY | O_CREAT | O_APPEND, 0600);
    int length = snprintf(line, sizeof(line), "%u %u\n", group, character);
    assert(fd >= 0);
    assert(write(fd, line, (size_t)length) == length);
    close(fd);
    return 1;
}

static void put_request(const char *dir, const char *id, const char *content) {
    char path[MAX_PATH_SIZE];
    int fd;
    assert(snprintf(path, sizeof(path), "%s/%s.req", dir, id) < (int)sizeof(path));
    fd = open(path, O_WRONLY | O_CREAT | O_EXCL, 0600);
    assert(fd >= 0);
    assert(write(fd, content, strlen(content)) == (ssize_t)strlen(content));
    close(fd);
}

int main(void) {
    char dir[] = "/tmp/dnf-online-mail-XXXXXX";
    char done[MAX_PATH_SIZE], buffer[64], content[64];
    const char *id = "0123456789abcdef0123456789abcdef";
    int i, fd, length, status;
    assert(mkdtemp(dir) != NULL);
    assert(snprintf(notification_log, sizeof(notification_log), "%s/log", dir) < (int)sizeof(notification_log));
    assert(snprintf(content, sizeof(content), "3 1 %lu\n", (unsigned long)time(NULL) + 30) < (int)sizeof(content));
    put_request(dir, id, content);
    assert(process_requests(dir, 2, notify_test) == 0);
    for (i = 0; i < 4; i++) {
        pid_t child = fork();
        assert(child >= 0);
        if (child == 0) { process_requests(dir, 3, notify_test); _exit(0); }
    }
    for (i = 0; i < 4; i++) { assert(wait(&status) > 0); assert(WIFEXITED(status) && WEXITSTATUS(status) == 0); }
    assert(snprintf(done, sizeof(done), "%s/%s.done", dir, id) < (int)sizeof(done));
    assert(access(done, F_OK) == 0);
    fd = open(notification_log, O_RDONLY);
    assert(fd >= 0);
    length = (int)read(fd, buffer, sizeof(buffer) - 1);
    close(fd);
    assert(length > 0);
    buffer[length] = '\0';
    assert(strcmp(buffer, "3 1\n") == 0);
    put_request(dir, id, content);
    assert(process_requests(dir, 3, notify_test) == 0);
    fd = open(notification_log, O_RDONLY);
    assert(fd >= 0);
    length = (int)read(fd, buffer, sizeof(buffer) - 1);
    close(fd);
    assert(length == 4);
    put_request(dir, "fedcba9876543210fedcba9876543210", "3 1 1\n");
    assert(process_requests(dir, 3, notify_test) == 1);
    assert(snprintf(done, sizeof(done), "%s/%s.done", dir, "fedcba9876543210fedcba9876543210") < (int)sizeof(done));
    fd = open(done, O_RDONLY);
    assert(fd >= 0);
    length = (int)read(fd, buffer, sizeof(buffer) - 1);
    close(fd);
    assert(length == 8);
    buffer[length] = '\0';
    assert(strcmp(buffer, "expired\n") == 0);
    unlink(done);
    unlink(notification_log);
    assert(snprintf(done, sizeof(done), "%s/%s.done", dir, id) < (int)sizeof(done));
    unlink(done);
    assert(rmdir(dir) == 0);
    return 0;
}
