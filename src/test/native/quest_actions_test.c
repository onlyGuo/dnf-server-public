#define DNF_MAIL_TEST
#include "../../../deploy/integrated/online-mail/mail_notify.c"
#define DNF_QUEST_TEST
static unsigned int server_group = 3;
static const char *mail_dir;
#include "../../../deploy/integrated/online-mail/quest_actions.h"
#include <assert.h>
#include <string.h>

static int state[0x7600 / 4];
static int saves, packets, stage;
static void *manager_stub(void) { return state; }
static void *user_stub(void *manager, unsigned int account) { assert(manager == state); return account == 2 ? state : NULL; }
static unsigned int character_stub(void *user) { assert(user == state); return 1; }
static void *read_stub(void *user) { assert(user == state); return state; }
static void *write_stub(void *user) { saves++; return read_stub(user); }
static void init_stub(void *packet) { (void)packet; stage = 1; }
static void header_stub(void *packet, int type, int id) { (void)packet; assert(stage++ == 1 && type == 1 && id == 35); }
static void byte_stub(void *packet, int value) { (void)packet; assert(stage++ == 2 && value == 1); }
static void short_stub(void *packet, int value) { (void)packet; assert(stage++ == 3 && value == 101); }
static void int_stub(void *packet, int value) { (void)packet; assert(stage++ == 4 && value == 0); }
static void finalize_stub(void *packet, int value) { (void)packet; assert(stage++ == 5 && value == 1); }
static void send_stub(void *user, void *packet) { (void)packet; assert(user == state && stage++ == 6); packets++; }
static void free_stub(void *packet) { (void)packet; assert(stage++ == 7); }

int main(void) {
    char request[2048], result[1024];
    unsigned long expiry = (unsigned long)time(NULL) + 30;
    int length, i;
    quest_manager = manager_stub; quest_user = user_stub; quest_character = character_stub;
    quest_read = read_stub; quest_write = write_stub;
    quest_packet_init = init_stub; quest_packet_header = header_stub; quest_packet_byte = byte_stub;
    quest_packet_short = short_stub; quest_packet_int = int_stub; quest_packet_finalize = finalize_stub;
    quest_packet_send = send_stub; quest_packet_free = free_stub;
    state[QUEST_IDS_OFFSET / 4] = 101; state[QUEST_TRIGGERS_OFFSET / 4] = 5;
    snprintf(request, sizeof(request), "3 2 1 %lu 0 0\n", expiry);
    assert(quest_execute(request, result, sizeof(result), 1) > 0);
    assert(strncmp(result, "snapshot 101 5 0 0", 18) == 0);
    assert(saves == 0 && packets == 0);
    snprintf(request, sizeof(request), "3 9 1 %lu 0 0\n", expiry);
    assert(quest_execute(request, result, sizeof(result), 1) == 0);
    snprintf(request, sizeof(request), "3 2 7 %lu 0 0\n", expiry);
    assert(quest_execute(request, result, sizeof(result), 1) > 0 && strcmp(result, "other-character\n") == 0);
    length = snprintf(request, sizeof(request), "3 2 1 %lu 1 1", expiry);
    for (i = 0; i < QUEST_SLOTS; i++) length += snprintf(request + length, sizeof(request) - length, " %d %d", i == 0 ? 101 : 0, i == 0 ? 5 : 0);
    snprintf(request + length, sizeof(request) - length, " 101\n");
    assert(quest_execute(request, result, sizeof(result), 0) > 0 && strcmp(result, "completed\n") == 0);
    assert(saves == 0 && state[QUEST_TRIGGERS_OFFSET / 4] == 5);
    state[QUEST_TRIGGERS_OFFSET / 4] = 6;
    assert(quest_execute(request, result, sizeof(result), 1) > 0 && strcmp(result, "changed\n") == 0);
    assert(saves == 0 && packets == 0);
    state[QUEST_TRIGGERS_OFFSET / 4] = 5;
    assert(quest_execute(request, result, sizeof(result), 1) > 0 && strcmp(result, "completed\n") == 0);
    assert(saves == 1 && packets == 1 && state[QUEST_TRIGGERS_OFFSET / 4] == 0);
    assert(quest_execute(request, result, sizeof(result), 1) > 0 && strcmp(result, "changed\n") == 0);
    assert(saves == 1 && packets == 1);
    snprintf(request, sizeof(request), "3 2 1 1 0 0\n");
    assert(quest_execute(request, result, sizeof(result), 1) > 0 && strcmp(result, "expired\n") == 0);
    assert(quest_execute("3 2 1 99999999999 0 0\n", result, sizeof(result), 1) == -1);
    return 0;
}
