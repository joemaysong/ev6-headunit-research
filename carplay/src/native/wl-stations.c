/* Root-only fixed Broadcom assoclist query via PTY; 3 s and 2 KiB limits. */
#define _POSIX_C_SOURCE 200809L
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>
#ifdef __linux__
#include <linux/tty.h>
#endif
static long long millis(void) {
    struct timespec t;
    if (clock_gettime(CLOCK_MONOTONIC, &t)) return -1;
    return (long long)t.tv_sec * 1000 + t.tv_nsec / 1000000;
}
int main(int argc, char **argv) {
    (void)argv;
    if (argc != 1 || getuid() != 0 || geteuid() != 0) return 2;
    int master = open("/dev/ptmx", O_RDWR | O_NOCTTY);
    int unlock = 0; unsigned int number = 0;
    if (master < 0 || ioctl(master, TIOCSPTLCK, &unlock) || ioctl(master, TIOCGPTN, &number)) return 3;
    char path[64];
    if (snprintf(path, sizeof(path), "/dev/pts/%u", number) >= (int)sizeof(path)) return 3;
    long long start = millis();
    if (start < 0) return 3;
    pid_t child = fork();
    if (child < 0) return 3;
    if (!child) {
        if (setsid() < 0) _exit(126);
        int slave = open(path, O_RDWR);
        if (slave < 0 || ioctl(slave, TIOCSCTTY, 0)) _exit(126);
        if (dup2(slave, 0) < 0 || dup2(slave, 1) < 0 || dup2(slave, 2) < 0) _exit(126);
        if (slave > 2) close(slave);
        close(master);
        char *const args[] = {"/system/bin/wl", "-i", "wlan0", "assoclist", NULL};
        char *const env[] = {"PATH=/system/bin", "LANG=C", NULL};
        execve(args[0], args, env); _exit(127);
    }
    unsigned char output[2048]; size_t used = 0; int status = 0, exited = 0, failed = 0;
    for (;;) {
        long long now = millis();
        if (now < 0 || now - start >= 3000) { failed = 1; break; }
        struct pollfd fd = {master, POLLIN, 0};
        int ready = poll(&fd, 1, 20);
        if (ready < 0 && errno != EINTR) { failed = 1; break; }
        if (ready > 0 && (fd.revents & (POLLIN | POLLHUP | POLLERR))) {
            unsigned char block[256]; ssize_t count = read(master, block, sizeof(block));
            if (count > 0) {
                if (used + (size_t)count > sizeof(output)) { failed = 1; break; }
                memcpy(output + used, block, (size_t)count); used += (size_t)count;
            } else if (count == 0 || (count < 0 && errno == EIO)) {
                break;
            } else if (errno != EINTR && errno != EAGAIN) { failed = 1; break; }
        }
        if (!exited) {
            pid_t result = waitpid(child, &status, WNOHANG);
            if (result == child) exited = 1;
            else if (result < 0 && errno != EINTR) { failed = 1; break; }
        }
        if (exited && ready == 0) break;
    }
    close(master);
    if (!exited) {
        if (failed) kill(child, SIGKILL);
        /* PTY EOF is not process completion. Bound the remaining wait too. */
        for (;;) {
            pid_t result = waitpid(child, &status, WNOHANG);
            if (result == child) { exited = 1; break; }
            if (result < 0 && errno != EINTR) { failed = 1; break; }
            long long now = millis();
            if (now < 0 || now - start >= 3000) {
                failed = 1; kill(child, SIGKILL);
                while (waitpid(child, &status, 0) < 0 && errno == EINTR) {}
                break;
            }
            struct timespec delay = {0, 1000000}; nanosleep(&delay, NULL);
        }
    }
    if (failed || !exited || !WIFEXITED(status) || WEXITSTATUS(status) != 0 || used == 0) return 4;
    size_t sent = 0;
    while (sent < used) {
        ssize_t count = write(1, output + sent, used - sent);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) return 5;
        sent += (size_t)count;
    }
    return 0;
}
