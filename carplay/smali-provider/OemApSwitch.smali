.class public LOemApSwitch;
.super Ljava/lang/Object;


# static fields
.field public static eventPath:Ljava/lang/String; = "/data/data/org.ev6.wifiswitchobserver/files/event-state"

.field public static wifi:Landroid/net/wifi/IWifiManager;


# direct methods
.method public static ap(Z)V
    .registers 5

    sget-object v0, LOemApSwitch;->wifi:Landroid/net/wifi/IWifiManager;

    invoke-interface {v0}, Landroid/net/wifi/IWifiManager;->getWifiApEnabledState()I

    move-result v1

    const/16 v2, 0xb

    if-eqz p0, :cond_c

    const/16 v2, 0xd

    :cond_c
    if-eq v1, v2, :cond_2e

    const-string v1, "android.net.wifi.WIFI_AP_STATE_CHANGED"

    invoke-static {v1, v2}, LOemApSwitch;->arm(Ljava/lang/String;I)V

    if-eqz p0, :cond_19

    invoke-interface {v0, p0}, Landroid/net/wifi/IWifiManager;->setEnableWIFIAPMode(Z)Z

    move-result v1

    :cond_19
    const/4 v1, 0x0

    invoke-interface {v0, v1, p0}, Landroid/net/wifi/IWifiManager;->setWifiApEnabledForConnectivity(Landroid/net/wifi/WifiConfiguration;Z)V

    invoke-static {}, LOemApSwitch;->waitState()V

    invoke-interface {v0}, Landroid/net/wifi/IWifiManager;->getWifiApEnabledState()I

    move-result v1

    if-eq v1, v2, :cond_2e

    new-instance v0, Ljava/lang/IllegalStateException;

    const-string v1, "OEM AP state readback differs"

    invoke-direct {v0, v1}, Ljava/lang/IllegalStateException;-><init>(Ljava/lang/String;)V

    throw v0

    :cond_2e
    if-nez p0, :cond_34

    invoke-interface {v0, p0}, Landroid/net/wifi/IWifiManager;->setEnableWIFIAPMode(Z)Z

    move-result v1

    :cond_34
    return-void
.end method

.method public static arm(Ljava/lang/String;I)V
    .registers 4

    new-instance v0, Ljava/io/File;

    sget-object v1, LOemApSwitch;->eventPath:Ljava/lang/String;

    invoke-direct {v0, v1}, Ljava/io/File;-><init>(Ljava/lang/String;)V

    invoke-virtual {v0}, Ljava/io/File;->delete()Z

    move-result v1

    sput-object p0, LOemWifiStateReceiver;->action:Ljava/lang/String;

    sput p1, LOemWifiStateReceiver;->target:I

    new-instance v0, Ljava/util/concurrent/CountDownLatch;

    const/4 v1, 0x1

    invoke-direct {v0, v1}, Ljava/util/concurrent/CountDownLatch;-><init>(I)V

    sput-object v0, LOemWifiStateReceiver;->latch:Ljava/util/concurrent/CountDownLatch;

    return-void
.end method

.method public static client(Z)V
    .registers 5

    sget-object v0, LOemApSwitch;->wifi:Landroid/net/wifi/IWifiManager;

    invoke-interface {v0}, Landroid/net/wifi/IWifiManager;->getWifiEnabledState()I

    move-result v1

    const/4 v2, 0x1

    if-eqz p0, :cond_a

    const/4 v2, 0x3

    :cond_a
    if-eq v1, v2, :cond_20

    const-string v1, "android.net.wifi.WIFI_STATE_CHANGED"

    invoke-static {v1, v2}, LOemApSwitch;->arm(Ljava/lang/String;I)V

    invoke-interface {v0, p0}, Landroid/net/wifi/IWifiManager;->setWifiEnabled(Z)Z

    move-result v1

    if-eqz v1, :cond_21

    invoke-static {}, LOemApSwitch;->waitState()V

    invoke-interface {v0}, Landroid/net/wifi/IWifiManager;->getWifiEnabledState()I

    move-result v1

    if-ne v1, v2, :cond_21

    :cond_20
    return-void

    :cond_21
    new-instance v0, Ljava/lang/IllegalStateException;

    const-string v1, "OEM client request rejected or readback differs"

    invoke-direct {v0, v1}, Ljava/lang/IllegalStateException;-><init>(Ljava/lang/String;)V

    throw v0
.end method

.method public static main([Ljava/lang/String;)V
    .registers 10

    array-length v4, p0

    const/4 v5, 0x2

    if-le v4, v5, :cond_8

    aget-object v4, p0, v5

    sput-object v4, LOemApSwitch;->eventPath:Ljava/lang/String;

    :cond_8
    const-string v4, "wifi"

    invoke-static {v4}, Landroid/os/ServiceManager;->getService(Ljava/lang/String;)Landroid/os/IBinder;

    move-result-object v4

    invoke-static {v4}, Landroid/net/wifi/IWifiManager$Stub;->asInterface(Landroid/os/IBinder;)Landroid/net/wifi/IWifiManager;

    move-result-object v4

    sput-object v4, LOemApSwitch;->wifi:Landroid/net/wifi/IWifiManager;

    :try_start_14
    const/4 v5, 0x0

    aget-object v3, p0, v5

    const/4 v5, 0x1

    aget-object v6, p0, v5

    new-instance v7, Ljava/io/File;

    invoke-direct {v7, v6}, Ljava/io/File;-><init>(Ljava/lang/String;)V

    const-string v6, "start"

    invoke-virtual {v6, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v6

    if-eqz v6, :cond_53

    invoke-virtual {v7}, Ljava/io/File;->exists()Z

    move-result v6

    if-nez v6, :cond_8c

    invoke-interface {v4}, Landroid/net/wifi/IWifiManager;->getWifiApEnabledState()I

    move-result v6

    const/16 v8, 0xb

    if-ne v6, v8, :cond_8c

    invoke-interface {v4}, Landroid/net/wifi/IWifiManager;->getWifiEnabledState()I

    move-result v6

    const/4 v8, 0x3

    if-eq v6, v8, :cond_3f

    const/4 v8, 0x1

    if-ne v6, v8, :cond_8c

    :cond_3f
    new-instance v8, Ljava/io/FileOutputStream;

    invoke-direct {v8, v7}, Ljava/io/FileOutputStream;-><init>(Ljava/io/File;)V

    invoke-virtual {v8, v6}, Ljava/io/FileOutputStream;->write(I)V

    invoke-virtual {v8}, Ljava/io/FileOutputStream;->close()V

    const/4 v5, 0x0

    invoke-static {v5}, LOemApSwitch;->client(Z)V

    const/4 v5, 0x1

    invoke-static {v5}, LOemApSwitch;->ap(Z)V

    goto :goto_80

    :cond_53
    const-string v6, "stop"

    invoke-virtual {v6, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v6

    if-eqz v6, :cond_8c

    invoke-virtual {v7}, Ljava/io/File;->exists()Z

    move-result v6

    if-eqz v6, :cond_8c

    const/4 v5, 0x0

    invoke-static {v5}, LOemApSwitch;->ap(Z)V

    new-instance v8, Ljava/io/FileInputStream;

    invoke-direct {v8, v7}, Ljava/io/FileInputStream;-><init>(Ljava/io/File;)V

    invoke-virtual {v8}, Ljava/io/FileInputStream;->read()I

    move-result v6

    invoke-virtual {v8}, Ljava/io/FileInputStream;->close()V

    const/4 v8, 0x3

    if-eq v6, v8, :cond_78

    const/4 v8, 0x1

    if-ne v6, v8, :cond_8c

    goto :goto_7c

    :cond_78
    const/4 v5, 0x1

    invoke-static {v5}, LOemApSwitch;->client(Z)V

    :goto_7c
    invoke-virtual {v7}, Ljava/io/File;->delete()Z

    move-result v5

    :goto_80
    sget-object v4, Ljava/lang/System;->out:Ljava/io/PrintStream;

    const-string v5, "OEM_SWITCH_COMPLETE"

    invoke-virtual {v4, v5}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V

    const/4 v4, 0x0

    invoke-static {v4}, Ljava/lang/System;->exit(I)V
    :try_end_8b
    .catch Ljava/lang/Throwable; {:try_start_14 .. :try_end_8b} :catch_94

    return-void

    :cond_8c
    new-instance v4, Ljava/lang/IllegalStateException;

    const-string v5, "Wrong operation or saved state/precondition invalid"

    invoke-direct {v4, v5}, Ljava/lang/IllegalStateException;-><init>(Ljava/lang/String;)V

    throw v4

    :catch_94
    move-exception v4

    sget-object v5, Ljava/lang/System;->out:Ljava/io/PrintStream;

    const-string v6, "OEM_SWITCH_FAILED"

    invoke-virtual {v5, v6}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V

    invoke-virtual {v4}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    move-result-object v4

    invoke-virtual {v4}, Ljava/lang/Class;->getSimpleName()Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v5, v4}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V

    const/4 v4, 0x1

    invoke-static {v4}, Ljava/lang/System;->exit(I)V

    return-void
.end method

.method public static waitState()V
    .registers 8

    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    sget-object v1, LOemWifiStateReceiver;->action:Ljava/lang/String;

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v1, ":"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    sget v1, LOemWifiStateReceiver;->target:I

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    const/16 v1, 0x96

    :cond_1a
    :try_start_1a
    new-instance v2, Ljava/io/FileInputStream;

    sget-object v3, LOemApSwitch;->eventPath:Ljava/lang/String;

    invoke-direct {v2, v3}, Ljava/io/FileInputStream;-><init>(Ljava/lang/String;)V

    const/16 v3, 0x64

    new-array v3, v3, [B

    invoke-virtual {v2, v3}, Ljava/io/FileInputStream;->read([B)I

    move-result v4

    invoke-virtual {v2}, Ljava/io/FileInputStream;->close()V

    if-lez v4, :cond_3e

    new-instance v2, Ljava/lang/String;

    const/4 v5, 0x0

    const-string v6, "US-ASCII"

    invoke-direct {v2, v3, v5, v4, v6}, Ljava/lang/String;-><init>([BIILjava/lang/String;)V

    invoke-virtual {v0, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-nez v2, :cond_4f
    :try_end_3c
    .catch Ljava/io/IOException; {:try_start_1a .. :try_end_3c} :catch_3d

    goto :goto_3e

    :catch_3d
    move-exception v2

    :cond_3e
    :goto_3e
    const-wide/16 v2, 0xc8

    invoke-static {v2, v3}, Ljava/lang/Thread;->sleep(J)V

    add-int/lit8 v1, v1, -0x1

    if-gtz v1, :cond_1a

    new-instance v0, Ljava/lang/IllegalStateException;

    const-string v1, "OEM state event missing; diagnostic safety timeout"

    invoke-direct {v0, v1}, Ljava/lang/IllegalStateException;-><init>(Ljava/lang/String;)V

    throw v0

    :cond_4f
    sget-object v1, Ljava/lang/System;->out:Ljava/io/PrintStream;

    invoke-virtual {v1, v0}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V

    return-void
.end method
