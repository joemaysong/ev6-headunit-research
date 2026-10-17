.class public LFinalStatusListener;
.super Landroid/phoneprojection/IWLStatusListener$Stub;


# instance fields
.field public connected:Z

.field public events:I


# direct methods
.method public constructor <init>()V
    .registers 1

    invoke-direct {p0}, Landroid/phoneprojection/IWLStatusListener$Stub;-><init>()V

    return-void
.end method


# virtual methods
.method public onWirelessConnected(ILjava/lang/String;)V
    .registers 4

    const/4 v0, 0x1

    iput-boolean v0, p0, LFinalStatusListener;->connected:Z

    iget v0, p0, LFinalStatusListener;->events:I

    add-int/lit8 v0, v0, 0x1

    iput v0, p0, LFinalStatusListener;->events:I

    invoke-static {}, LCarPlayController;->wake()V

    return-void
.end method

.method public onWirelessDisconnected(ILjava/lang/String;)V
    .registers 4

    const/4 v0, 0x0

    iput-boolean v0, p0, LFinalStatusListener;->connected:Z

    iget v0, p0, LFinalStatusListener;->events:I

    add-int/lit8 v0, v0, 0x1

    iput v0, p0, LFinalStatusListener;->events:I

    invoke-static {}, LCarPlayController;->wake()V

    return-void
.end method
