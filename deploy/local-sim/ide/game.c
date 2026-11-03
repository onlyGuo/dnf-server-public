/* A local stand-in with the same executable name, arguments and TCP ports as DNF. */
#include <arpa/inet.h>
#include <errno.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

static volatile sig_atomic_t running = 1;
static void stop(int signal_number) { (void)signal_number; running = 0; }

int main(int argc, char **argv) {
    if (argc != 3 || strncmp(argv[1], "siroco", 6) != 0 || strcmp(argv[2], "start") != 0) return 2;
    int channel = atoi(argv[1] + 6);
    if (channel < 1 || channel > 99) return 2;
    int server = socket(AF_INET, SOCK_STREAM, 0);
    if (server < 0) return 1;
    int reuse = 1;
    setsockopt(server, SOL_SOCKET, SO_REUSEADDR, &reuse, sizeof(reuse));
    struct sockaddr_in address;
    memset(&address, 0, sizeof(address));
    address.sin_family = AF_INET;
    address.sin_port = htons((unsigned short)(30000 + channel));
    address.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    if (bind(server, (struct sockaddr *)&address, sizeof(address)) || listen(server, 8)) {
        perror("local channel listener"); close(server); return 1;
    }
    struct sigaction handler;
    memset(&handler, 0, sizeof(handler));
    handler.sa_handler = stop;
    sigemptyset(&handler.sa_mask);
    sigaction(SIGTERM, &handler, NULL);
    sigaction(SIGINT, &handler, NULL);
    while (running) {
        int client = accept(server, NULL, NULL);
        if (client >= 0) close(client);
        else if (errno != EINTR) break;
    }
    close(server);
    return 0;
}
