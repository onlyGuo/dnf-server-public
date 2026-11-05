#define DNF_MAIL_TEST
#include "../../../deploy/integrated/online-mail/mail_notify.c"

static int notify_test(unsigned int group, unsigned int character) {
    return group == 3 && character == 1;
}

int main(int argc, char **argv) {
    if (argc != 2) return 2;
    return process_requests(argv[1], 3, notify_test) == 1 ? 0 : 1;
}
