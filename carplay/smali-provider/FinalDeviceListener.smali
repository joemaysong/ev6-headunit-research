.class public LFinalDeviceListener;
.super Landroid/phoneprojection/IWLDeviceListener$Stub;


# instance fields
.field public events:I

.field public oob:Z

.field private registry:LOfflineRegistry;


# direct methods
.method public constructor <init>(LOfflineRegistry;)V
    .registers 2

    invoke-direct {p0}, Landroid/phoneprojection/IWLDeviceListener$Stub;-><init>()V

    iput-object p1, p0, LFinalDeviceListener;->registry:LOfflineRegistry;

    return-void
.end method


# virtual methods
.method public isCPAutoConnectionAvailableDevice(Ljava/lang/String;)Z
    .registers 4

    iget-object v0, p0, LFinalDeviceListener;->registry:LOfflineRegistry;

    invoke-virtual {v0, p1}, LOfflineRegistry;->getWLDeviceInfo(Ljava/lang/String;)Landroid/phoneprojection/WLDeviceInfo;

    move-result-object v0

    if-eqz v0, :cond_1a

    invoke-virtual {v0}, Landroid/phoneprojection/WLDeviceInfo;->getWLDType()I

    move-result v1

    const/4 v0, 0x2

    if-ne v1, v0, :cond_1a

    iget-object v0, p0, LFinalDeviceListener;->registry:LOfflineRegistry;

    invoke-virtual {v0, p1}, LOfflineRegistry;->getWLDeviceInfo(Ljava/lang/String;)Landroid/phoneprojection/WLDeviceInfo;

    move-result-object v0

    invoke-virtual {v0}, Landroid/phoneprojection/WLDeviceInfo;->getWLDAutoConnection()Z

    move-result v0

    return v0

    :cond_1a
    const/4 v0, 0x0

    return v0
.end method

.method public onCarPlaySupportOOB(Z)V
    .registers 2

    iput-boolean p1, p0, LFinalDeviceListener;->oob:Z

    return-void
.end method

.method public requestWLDeviceConnectingState(Landroid/phoneprojection/WLDeviceInfo;I)V
    .registers 4

    iget v0, p0, LFinalDeviceListener;->events:I

    add-int/lit8 v0, v0, 0x1

    iput v0, p0, LFinalDeviceListener;->events:I

    return-void
.end method

.method public requestWLDeviceDisconnectedByUser(Landroid/phoneprojection/WLDeviceInfo;)V
    .registers 3

    iget v0, p0, LFinalDeviceListener;->events:I

    add-int/lit8 v0, v0, 0x1

    iput v0, p0, LFinalDeviceListener;->events:I

    if-eqz p1, :cond_f

    invoke-virtual {p1}, Landroid/phoneprojection/WLDeviceInfo;->getWLDevBtAddr()Ljava/lang/String;

    move-result-object v0

    invoke-static {v0}, LFinalRegistry;->pauseByUser(Ljava/lang/String;)V

    :cond_f
    return-void
.end method

.method public requestWLDeviceInfoAdd(Landroid/phoneprojection/WLDeviceInfo;)V
    .registers 3

    iget v0, p0, LFinalDeviceListener;->events:I

    add-int/lit8 v0, v0, 0x1

    iput v0, p0, LFinalDeviceListener;->events:I

    iget-object v0, p0, LFinalDeviceListener;->registry:LOfflineRegistry;

    invoke-virtual {v0, p1}, LOfflineRegistry;->put(Landroid/phoneprojection/WLDeviceInfo;)V

    if-eqz p1, :cond_14

    invoke-virtual {p1}, Landroid/phoneprojection/WLDeviceInfo;->getWLDevBtAddr()Ljava/lang/String;

    move-result-object v0

    invoke-static {v0}, LCarPlayController;->deviceUpdated(Ljava/lang/String;)V

    :cond_14
    return-void
.end method

.method public requestWLDeviceInfoUpdate(Landroid/phoneprojection/WLDeviceInfo;)V
    .registers 3

    iget v0, p0, LFinalDeviceListener;->events:I

    add-int/lit8 v0, v0, 0x1

    iput v0, p0, LFinalDeviceListener;->events:I

    iget-object v0, p0, LFinalDeviceListener;->registry:LOfflineRegistry;

    invoke-virtual {v0, p1}, LOfflineRegistry;->put(Landroid/phoneprojection/WLDeviceInfo;)V

    if-eqz p1, :cond_14

    invoke-virtual {p1}, Landroid/phoneprojection/WLDeviceInfo;->getWLDevBtAddr()Ljava/lang/String;

    move-result-object v0

    invoke-static {v0}, LCarPlayController;->deviceUpdated(Ljava/lang/String;)V

    :cond_14
    return-void
.end method

.method public requestWLDeviceStatusUpdate(Landroid/phoneprojection/WLDeviceInfo;II)V
    .registers 5

    iget v0, p0, LFinalDeviceListener;->events:I

    add-int/lit8 v0, v0, 0x1

    iput v0, p0, LFinalDeviceListener;->events:I

    return-void
.end method
