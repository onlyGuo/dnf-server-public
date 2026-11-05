/* Fixed-format local quest operations, executed only from the game's main loop. */
static void *(* volatile quest_manager)(void) = (void *(*)(void))(uintptr_t)0x080cc18e;
static void *(* volatile quest_user)(void *, unsigned int) = (void *(*)(void *, unsigned int))(uintptr_t)0x082948c6;
static unsigned int (* volatile quest_character)(void *) = (unsigned int (*)(void *))(uintptr_t)0x080cbc4e;
static void *(* volatile quest_read)(void *) = (void *(*)(void *))(uintptr_t)0x0819a8a6;
static void *(* volatile quest_write)(void *) = (void *(*)(void *))(uintptr_t)0x0814aa5e;
static void (* volatile quest_packet_init)(void *) = (void (*)(void *))(uintptr_t)0x0858dd4c;
static void (* volatile quest_packet_free)(void *) = (void (*)(void *))(uintptr_t)0x0858de80;
static void (* volatile quest_packet_header)(void *, int, int) = (void (*)(void *, int, int))(uintptr_t)0x080cb8fc;
static void (* volatile quest_packet_byte)(void *, int) = (void (*)(void *, int))(uintptr_t)0x080cb920;
static void (* volatile quest_packet_short)(void *, int) = (void (*)(void *, int))(uintptr_t)0x080d9ea4;
static void (* volatile quest_packet_int)(void *, int) = (void (*)(void *, int))(uintptr_t)0x080cb93c;
static void (* volatile quest_packet_finalize)(void *, int) = (void (*)(void *, int))(uintptr_t)0x080cb958;
static void (* volatile quest_packet_send)(void *, void *) = (void (*)(void *, void *))(uintptr_t)0x086485ba;

/* Layout established from UserQuest::set_trigger and get_quest. */
#define QUEST_IDS_OFFSET 0x7538
#define QUEST_TRIGGERS_OFFSET 0x7588
#define QUEST_SLOTS 20

static int quest_number(const char **cursor, unsigned int *value) {
    const char *p = *cursor;
    unsigned int number = 0;
    if (*p < '0' || *p > '9') return 0;
    do {
        unsigned int digit = (unsigned int)(*p++ - '0');
        if (number > (2147483647U - digit) / 10) return 0;
        number = number * 10 + digit;
    } while (*p >= '0' && *p <= '9');
    if (*p != ' ' && *p != '\n' && *p != '\0') return 0;
    *value = number;
    *cursor = *p == ' ' ? p + 1 : p;
    return 1;
}

static int quest_supported(void) {
#ifdef DNF_QUEST_TEST
    return 1;
#else
    /* Additional code fingerprints protect quest-specific entry points and data layout. */
    static int supported = -1;
    static const unsigned int functions[][3] = {
        {0x080cc18eU, 13, 0xf3bb8096U}, {0x082948c6U, 310, 0xa8449e23U},
        {0x080cbc4eU, 30, 0xe4809abaU}, {0x0819a8a6U, 13, 0x471e1821U},
        {0x0814aa5eU, 27, 0x0694efc3U}, {0x0858dd4cU, 32, 0xbd263090U},
        {0x0858de80U, 32, 0xcad9c278U}, {0x080cb8fcU, 36, 0xed7a79c8U},
        {0x080cb920U, 28, 0x94126c19U}, {0x080d9ea4U, 32, 0x99dae25dU},
        {0x080cb93cU, 28, 0xc81267e1U}, {0x080cb958U, 32, 0xb3363269U},
        {0x086485baU, 32, 0xa8a2738cU}, {0x086ac6acU, 424, 0x564a45feU}
    };
    unsigned int i, j;
    if (supported >= 0) return supported;
    supported = 0;
    for (i = 0; i < sizeof(functions) / sizeof(functions[0]); i++) {
        const unsigned char *code = (const unsigned char *)(uintptr_t)functions[i][0];
        unsigned int hash = 2166136261U;
        for (j = 0; j < functions[i][1]; j++) hash = (hash ^ code[j]) * 16777619U;
        if (hash != functions[i][2]) return 0;
    }
    supported = 1;
    return 1;
#endif
}

static int quest_execute(const char *request, char *result, size_t capacity, int apply) {
    const char *cursor = request;
    unsigned int group, account, character, expiry, action, count, selected[QUEST_SLOTS];
    unsigned int expected[QUEST_SLOTS * 2];
    void *manager, *user, *quests;
    int i, j, length;
    if (!quest_number(&cursor, &group) || group < 1 || group > 6) return -1;
    if (group != server_group) return 0;
    if (!quest_number(&cursor, &account) || !account
        || !quest_number(&cursor, &character) || !character
        || !quest_number(&cursor, &expiry)
        || !quest_number(&cursor, &action) || action > 1
        || !quest_number(&cursor, &count) || count > QUEST_SLOTS) return -1;
    if ((unsigned long)time(NULL) > expiry) return snprintf(result, capacity, "expired\n");
    if (!quest_supported()) return snprintf(result, capacity, "unsupported\n");
    if (action == 0 && count != 0) return -1;
    if (action == 1) {
        for (i = 0; i < QUEST_SLOTS * 2; i++) if (!quest_number(&cursor, &expected[i])) return -1;
        for (i = 0; i < (int)count; i++) {
            if (!quest_number(&cursor, &selected[i]) || !selected[i] || selected[i] > 65535) return -1;
            for (j = 0; j < i; j++) if (selected[j] == selected[i]) return -1;
        }
    }
    if (*cursor == '\n') cursor++;
    if (*cursor) return -1;
    manager = quest_manager();
    if (!manager) return 0;
    user = quest_user(manager, account);
    if (!user) return 0; /* Another channel may own this account. */
    if (quest_character(user) != character) return snprintf(result, capacity, "other-character\n");
    quests = quest_read(user);
    if (!quests) return 0;
    if (action == 0) {
        length = snprintf(result, capacity, "snapshot");
        for (i = 0; i < QUEST_SLOTS; i++) {
            int id = ((int *)((char *)quests + QUEST_IDS_OFFSET))[i];
            int trigger = ((int *)((char *)quests + QUEST_TRIGGERS_OFFSET))[i];
            if (id < 0 || trigger < 0) return snprintf(result, capacity, "unsupported\n");
            length += snprintf(result + length, capacity - length, " %d %d", id, trigger);
            if (length >= (int)capacity - 2) return -1;
        }
        return length + snprintf(result + length, capacity - length, "\n");
    }
    /* Validate the entire snapshot before touching any progress. */
    for (i = 0; i < QUEST_SLOTS; i++) {
        if (((unsigned int *)((char *)quests + QUEST_IDS_OFFSET))[i] != expected[i * 2]
            || ((unsigned int *)((char *)quests + QUEST_TRIGGERS_OFFSET))[i] != expected[i * 2 + 1])
            return snprintf(result, capacity, "changed\n");
    }
    for (j = 0; j < (int)count; j++) {
        for (i = 0; i < QUEST_SLOTS; i++) if (expected[i * 2] == selected[j] && expected[i * 2 + 1] > 0) break;
        if (i == QUEST_SLOTS) return snprintf(result, capacity, "changed\n");
    }
    if (count && apply) {
        unsigned int packet[16];
        quests = quest_write(user); /* Marks quest state for the game's normal persistence. */
        for (j = 0; j < (int)count; j++) for (i = 0; i < QUEST_SLOTS; i++)
            if (expected[i * 2] == selected[j]) ((int *)((char *)quests + QUEST_TRIGGERS_OFFSET))[i] = 0;
        /* Same successful progress packet as CUser::quest_action(35, quest, ...). */
        for (j = 0; j < (int)count; j++) {
            quest_packet_init(packet);
            quest_packet_header(packet, 1, 35);
            quest_packet_byte(packet, 1);
            quest_packet_short(packet, (int)selected[j]);
            quest_packet_int(packet, 0);
            quest_packet_finalize(packet, 1);
            quest_packet_send(user, packet);
            quest_packet_free(packet);
        }
    }
    return snprintf(result, capacity, "completed\n");
}

static void process_quest_requests(void) {
    char directory[MAX_PATH_SIZE];
    DIR *dir;
    struct dirent *entry;
    int processed = 0;
    if (!quest_supported()) return;
    if (snprintf(directory, sizeof(directory), "%s/quests", mail_dir) >= (int)sizeof(directory)) return;
    mkdir(directory, 0700);
    {
        char ready[MAX_PATH_SIZE];
        if (snprintf(ready, sizeof(ready), "%s/ready-%ld", directory, (long)getpid()) < (int)sizeof(ready)) {
            int ready_fd = open(ready, O_WRONLY | O_CREAT | O_TRUNC | O_NOFOLLOW, 0600);
            if (ready_fd >= 0) close(ready_fd);
        }
    }
    dir = opendir(directory);
    if (!dir) return;
    while (processed < 4 && (entry = readdir(dir)) != NULL) {
        char path[MAX_PATH_SIZE], claimed[MAX_PATH_SIZE], done[MAX_PATH_SIZE], temp[MAX_PATH_SIZE];
        char payload[2048], result[1024];
        int fd, length, size;
        if (!request_name(entry->d_name)) continue;
        if (snprintf(path, sizeof(path), "%s/%s", directory, entry->d_name) >= (int)sizeof(path)
            || snprintf(claimed, sizeof(claimed), "%s/%.*s.work", directory, 32, entry->d_name) >= (int)sizeof(claimed)
            || snprintf(done, sizeof(done), "%s/%.*s.done", directory, 32, entry->d_name) >= (int)sizeof(done)
            || snprintf(temp, sizeof(temp), "%s/%.*s.result.%ld", directory, 32, entry->d_name, (long)getpid()) >= (int)sizeof(temp)) continue;
        fd = open(path, O_RDONLY | O_NOFOLLOW);
        if (fd < 0) continue;
        length = (int)read(fd, payload, sizeof(payload) - 1);
        close(fd);
        if (length <= 0 || length >= (int)sizeof(payload) - 1) continue;
        payload[length] = '\0';
        if (access(done, F_OK) == 0) { unlink(path); continue; }
        /* The read-only lookup leaves the request for the owning channel. */
        size = quest_execute(payload, result, sizeof(result), 0);
        if (size == 0) continue;
        if (size < 0) { unlink(path); continue; }
        if (rename(path, claimed) != 0) continue;
        size = quest_execute(payload, result, sizeof(result), 1);
        if (size <= 0) size = snprintf(result, sizeof(result), "changed\n");
        fd = open(temp, O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW, 0600);
        if (fd >= 0) {
            if (write(fd, result, (size_t)size) == size) rename(temp, done);
            else unlink(temp);
            close(fd);
        }
        unlink(claimed);
        processed++;
    }
    closedir(dir);
}
