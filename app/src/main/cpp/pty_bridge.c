#include <jni.h>
#include <pty.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <errno.h>
#include <android/log.h>

#define TAG "ChronoPtyBridge"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

JNIEXPORT jintArray JNICALL
Java_com_chrono_ssh_core_service_PtyBridge_nativeForkPty(
    JNIEnv *env, jclass cls,
    jstring jCmd, jobjectArray jArgs, jobjectArray jEnvVars,
    jint rows, jint cols)
{
    jintArray result = (*env)->NewIntArray(env, 2);
    jint buf[2];

    const char *cmd = (*env)->GetStringUTFChars(env, jCmd, NULL);
    if (!cmd) {
        buf[0] = -1; buf[1] = ENOMEM;
        (*env)->SetIntArrayRegion(env, result, 0, 2, buf);
        return result;
    }

    int argc = (*env)->GetArrayLength(env, jArgs);
    char **argv = calloc(argc + 1, sizeof(char *));
    jobject *argRefs = calloc(argc, sizeof(jobject));
    for (int i = 0; i < argc; i++) {
        jstring s = (*env)->GetObjectArrayElement(env, jArgs, i);
        argRefs[i] = s;
        argv[i] = (char *)(*env)->GetStringUTFChars(env, s, NULL);
    }
    argv[argc] = NULL;

    int envc = (*env)->GetArrayLength(env, jEnvVars);
    char **envp = calloc(envc + 1, sizeof(char *));
    jobject *envRefs = calloc(envc, sizeof(jobject));
    for (int i = 0; i < envc; i++) {
        jstring s = (*env)->GetObjectArrayElement(env, jEnvVars, i);
        envRefs[i] = s;
        envp[i] = (char *)(*env)->GetStringUTFChars(env, s, NULL);
    }
    envp[envc] = NULL;

    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = rows;
    ws.ws_col = cols;

    int masterFd;
    pid_t pid = forkpty(&masterFd, NULL, NULL, &ws);

    if (pid < 0) {
        LOGE("forkpty failed: %s", strerror(errno));
        buf[0] = -1; buf[1] = errno;
    } else if (pid == 0) {
        setsid();
        execve(cmd, argv, envp);
        LOGE("execve(%s) failed: %s", cmd, strerror(errno));
        _exit(127);
    } else {
        buf[0] = masterFd;
        buf[1] = pid;
    }

    (*env)->ReleaseStringUTFChars(env, jCmd, cmd);
    for (int i = 0; i < argc; i++) {
        (*env)->ReleaseStringUTFChars(env, (jstring)argRefs[i], argv[i]);
        (*env)->DeleteLocalRef(env, argRefs[i]);
    }
    for (int i = 0; i < envc; i++) {
        (*env)->ReleaseStringUTFChars(env, (jstring)envRefs[i], envp[i]);
        (*env)->DeleteLocalRef(env, envRefs[i]);
    }
    free(argv);
    free(argRefs);
    free(envp);
    free(envRefs);

    (*env)->SetIntArrayRegion(env, result, 0, 2, buf);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_chrono_ssh_core_service_PtyBridge_nativeSetSize(
    JNIEnv *env, jclass cls,
    jint fd, jint rows, jint cols)
{
    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = rows;
    ws.ws_col = cols;
    return ioctl(fd, TIOCSWINSZ, &ws);
}

JNIEXPORT jint JNICALL
Java_com_chrono_ssh_core_service_PtyBridge_nativeWaitPid(
    JNIEnv *env, jclass cls, jint pid)
{
    int status;
    if (waitpid(pid, &status, 0) < 0) return -1;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}
