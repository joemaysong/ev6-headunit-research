.class public LWifiTrialProbe;
.super Ljava/lang/Object;


# direct methods
.method public static main([Ljava/lang/String;)V
    .registers 5

    const-string v0, "wifi"

    invoke-static {v0}, Landroid/os/ServiceManager;->getService(Ljava/lang/String;)Landroid/os/IBinder;

    move-result-object v0

    invoke-static {v0}, Landroid/net/wifi/IWifiManager$Stub;->asInterface(Landroid/os/IBinder;)Landroid/net/wifi/IWifiManager;

    move-result-object v0

    sget-object v2, Ljava/lang/System;->out:Ljava/io/PrintStream;

    const-string v1, "isWiFiSupport="

    invoke-virtual {v2, v1}, Ljava/io/PrintStream;->print(Ljava/lang/String;)V

    invoke-interface {v0}, Landroid/net/wifi/IWifiManager;->isWiFiSupport()I

    move-result v3

    invoke-virtual {v2, v3}, Ljava/io/PrintStream;->println(I)V

    const-string v1, "getWifiEnabledState="

    invoke-virtual {v2, v1}, Ljava/io/PrintStream;->print(Ljava/lang/String;)V

    invoke-interface {v0}, Landroid/net/wifi/IWifiManager;->getWifiEnabledState()I

    move-result v3

    invoke-virtual {v2, v3}, Ljava/io/PrintStream;->println(I)V

    const-string v1, "getWifiApEnabledState="

    invoke-virtual {v2, v1}, Ljava/io/PrintStream;->print(Ljava/lang/String;)V

    invoke-interface {v0}, Landroid/net/wifi/IWifiManager;->getWifiApEnabledState()I

    move-result v3

    invoke-virtual {v2, v3}, Ljava/io/PrintStream;->println(I)V

    const-string v1, "getEnableWIFIAPMode="

    invoke-virtual {v2, v1}, Ljava/io/PrintStream;->print(Ljava/lang/String;)V

    invoke-interface {v0}, Landroid/net/wifi/IWifiManager;->getEnableWIFIAPMode()Z

    move-result v3

    invoke-virtual {v2, v3}, Ljava/io/PrintStream;->println(Z)V

    const-string v1, "getWifiApMode="

    invoke-virtual {v2, v1}, Ljava/io/PrintStream;->print(Ljava/lang/String;)V

    invoke-interface {v0}, Landroid/net/wifi/IWifiManager;->getWifiApMode()I

    move-result v3

    invoke-virtual {v2, v3}, Ljava/io/PrintStream;->println(I)V

    const-string v1, "getPersistedWifiApEnabled="

    invoke-virtual {v2, v1}, Ljava/io/PrintStream;->print(Ljava/lang/String;)V

    invoke-interface {v0}, Landroid/net/wifi/IWifiManager;->getPersistedWifiApEnabled()Z

    move-result v3

    invoke-virtual {v2, v3}, Ljava/io/PrintStream;->println(Z)V

    const-string v1, "getPersistedWifiEnabled="

    invoke-virtual {v2, v1}, Ljava/io/PrintStream;->print(Ljava/lang/String;)V

    invoke-interface {v0}, Landroid/net/wifi/IWifiManager;->getPersistedWifiEnabled()Z

    move-result v3

    invoke-virtual {v2, v3}, Ljava/io/PrintStream;->println(Z)V

    return-void
.end method
