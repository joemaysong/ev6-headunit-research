.class public Lorg/ev6/research/WifiEvents;
.super Landroid/content/BroadcastReceiver;
.field private owner:Lorg/ev6/research/ProbeService;
.method public constructor <init>(Lorg/ev6/research/ProbeService;)V
 .locals 0
 invoke-direct {p0},Landroid/content/BroadcastReceiver;-><init>()V
 iput-object p1,p0,Lorg/ev6/research/WifiEvents;->owner:Lorg/ev6/research/ProbeService;
 return-void
.end method
.method public onReceive(Landroid/content/Context;Landroid/content/Intent;)V
 .locals 1
 iget-object v0,p0,Lorg/ev6/research/WifiEvents;->owner:Lorg/ev6/research/ProbeService;
 invoke-static {v0,p2},Lorg/ev6/research/BackendCallbacks;->wifi(Landroid/content/Context;Landroid/content/Intent;)V
 return-void
.end method
