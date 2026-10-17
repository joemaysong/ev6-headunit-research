.class public LFinalRegistry;
.super Ljava/lang/Object;


# static fields
.field private static devices:LFinalDeviceListener;

.field private static manager:Landroid/phoneprojection/IPPManager;

.field private static registry:LOfflineRegistry;

.field private static status:LFinalStatusListener;


# direct methods
.method public static close()V
    .registers 2

    sget-object v0, LFinalRegistry;->manager:Landroid/phoneprojection/IPPManager;

    if-eqz v0, :cond_14

    sget-object v1, LFinalRegistry;->devices:LFinalDeviceListener;

    invoke-interface {v0, v1}, Landroid/phoneprojection/IPPManager;->removeWLDeviceInfoListener(Landroid/phoneprojection/IWLDeviceListener;)V

    sget-object v1, LFinalRegistry;->status:LFinalStatusListener;

    invoke-interface {v0, v1}, Landroid/phoneprojection/IPPManager;->removeWirelessStatusListener(Landroid/phoneprojection/IWLStatusListener;)V

    const/4 v1, 0x0

    invoke-interface {v0, v1}, Landroid/phoneprojection/IPPManager;->bindIWLDeviceInfo(Landroid/phoneprojection/IWLDeviceInfo;)V

    sput-object v1, LFinalRegistry;->manager:Landroid/phoneprojection/IPPManager;

    :cond_14
    return-void
.end method

.method public static pauseByUser(Ljava/lang/String;)V
    .registers 1

    invoke-static {p0}, LCarPlayController;->userDisconnected(Ljava/lang/String;)V

    return-void
.end method

.method public static start()V
    .registers 4

    const-string v0, "phoneprojection"

    invoke-static {v0}, Landroid/os/ServiceManager;->getService(Ljava/lang/String;)Landroid/os/IBinder;

    move-result-object v0

    invoke-static {v0}, Landroid/phoneprojection/IPPManager$Stub;->asInterface(Landroid/os/IBinder;)Landroid/phoneprojection/IPPManager;

    move-result-object v0

    sput-object v0, LFinalRegistry;->manager:Landroid/phoneprojection/IPPManager;

    new-instance v1, LOfflineRegistry;

    invoke-direct {v1}, LOfflineRegistry;-><init>()V

    sput-object v1, LFinalRegistry;->registry:LOfflineRegistry;

    new-instance v2, LFinalDeviceListener;

    invoke-direct {v2, v1}, LFinalDeviceListener;-><init>(LOfflineRegistry;)V

    sput-object v2, LFinalRegistry;->devices:LFinalDeviceListener;

    new-instance v3, LFinalStatusListener;

    invoke-direct {v3}, LFinalStatusListener;-><init>()V

    sput-object v3, LFinalRegistry;->status:LFinalStatusListener;

    invoke-interface {v0, v1}, Landroid/phoneprojection/IPPManager;->bindIWLDeviceInfo(Landroid/phoneprojection/IWLDeviceInfo;)V

    invoke-interface {v0, v2}, Landroid/phoneprojection/IPPManager;->addWLDeviceInfoListener(Landroid/phoneprojection/IWLDeviceListener;)V

    invoke-interface {v0, v3}, Landroid/phoneprojection/IPPManager;->addWirelessStatusListener(Landroid/phoneprojection/IWLStatusListener;)V

    return-void
.end method
