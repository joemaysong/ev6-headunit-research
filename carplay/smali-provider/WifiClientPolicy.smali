.class public LWifiClientPolicy;
.super Ljava/lang/Object;


# direct methods
.method public static main([Ljava/lang/String;)V
    .registers 7

    const-string v0, "wifi"

    invoke-static {v0}, Landroid/os/ServiceManager;->getService(Ljava/lang/String;)Landroid/os/IBinder;

    move-result-object v0

    invoke-static {v0}, Landroid/net/wifi/IWifiManager$Stub;->asInterface(Landroid/os/IBinder;)Landroid/net/wifi/IWifiManager;

    move-result-object v0

    invoke-interface {v0}, Landroid/net/wifi/IWifiManager;->getWifiApEnabledState()I

    move-result v1

    const/16 v2, 0xb

    if-ne v1, v2, :cond_3a

    const/4 v2, 0x3

    const/4 v1, 0x1

    invoke-interface {v0, v1}, Landroid/net/wifi/IWifiManager;->setWifiEnabled(Z)Z

    move-result v1

    if-eqz v1, :cond_3a

    const/16 v3, 0x96

    :cond_1c
    invoke-interface {v0}, Landroid/net/wifi/IWifiManager;->getWifiEnabledState()I

    move-result v1

    if-eq v1, v2, :cond_2c

    const-wide/16 v4, 0xc8

    invoke-static {v4, v5}, Ljava/lang/Thread;->sleep(J)V

    add-int/lit8 v3, v3, -0x1

    if-gtz v3, :cond_1c

    goto :goto_3a

    :cond_2c
    invoke-interface {v0}, Landroid/net/wifi/IWifiManager;->getPersistedWifiEnabled()Z

    move-result v1

    if-eqz v1, :cond_3a

    sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;

    const-string v1, "WIFI_CLIENT_ENABLED_AND_PERSISTED"

    invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V

    return-void

    :cond_3a
    :goto_3a
    sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;

    const-string v1, "WIFI_CLIENT_POLICY_FAILED"

    invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V

    const/4 v0, 0x1

    invoke-static {v0}, Ljava/lang/System;->exit(I)V

    return-void
.end method
