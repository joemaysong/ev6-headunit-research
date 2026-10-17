.class public LOfflineRegistry;
.super Landroid/phoneprojection/IWLDeviceInfo$Stub;


# instance fields
.field public disconnectRequests:I

.field private records:Ljava/util/concurrent/ConcurrentHashMap;


# direct methods
.method public constructor <init>()V
    .registers 2

    invoke-direct {p0}, Landroid/phoneprojection/IWLDeviceInfo$Stub;-><init>()V

    new-instance v0, Ljava/util/concurrent/ConcurrentHashMap;

    invoke-direct {v0}, Ljava/util/concurrent/ConcurrentHashMap;-><init>()V

    iput-object v0, p0, LOfflineRegistry;->records:Ljava/util/concurrent/ConcurrentHashMap;

    return-void
.end method


# virtual methods
.method public disconnectWirelessAll()V
    .registers 2

    iget v0, p0, LOfflineRegistry;->disconnectRequests:I

    add-int/lit8 v0, v0, 0x1

    iput v0, p0, LOfflineRegistry;->disconnectRequests:I

    return-void
.end method

.method public getWLDeviceInfo(Ljava/lang/String;)Landroid/phoneprojection/WLDeviceInfo;
    .registers 3

    iget-object v0, p0, LOfflineRegistry;->records:Ljava/util/concurrent/ConcurrentHashMap;

    invoke-virtual {v0, p1}, Ljava/util/concurrent/ConcurrentHashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Landroid/phoneprojection/WLDeviceInfo;

    return-object v0
.end method

.method public put(Landroid/phoneprojection/WLDeviceInfo;)V
    .registers 4

    if-eqz p1, :cond_d

    invoke-virtual {p1}, Landroid/phoneprojection/WLDeviceInfo;->getWLDevBtAddr()Ljava/lang/String;

    move-result-object v0

    if-eqz v0, :cond_d

    iget-object v1, p0, LOfflineRegistry;->records:Ljava/util/concurrent/ConcurrentHashMap;

    invoke-virtual {v1, v0, p1}, Ljava/util/concurrent/ConcurrentHashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    :cond_d
    return-void
.end method
