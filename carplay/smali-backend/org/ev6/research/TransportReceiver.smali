.class public Lorg/ev6/research/TransportReceiver;
.super Landroid/content/BroadcastReceiver;
.field private service:Lorg/ev6/research/ProbeService;
.method public constructor <init>(Lorg/ev6/research/ProbeService;)V
 .locals 0
 invoke-direct {p0},Landroid/content/BroadcastReceiver;-><init>()V
 iput-object p1,p0,Lorg/ev6/research/TransportReceiver;->service:Lorg/ev6/research/ProbeService;
 return-void
.end method
.method public onReceive(Landroid/content/Context;Landroid/content/Intent;)V
 .locals 3
 iget-object v0,p0,Lorg/ev6/research/TransportReceiver;->service:Lorg/ev6/research/ProbeService;
 invoke-static {v0},Lorg/ev6/research/BackendOwnership;->current(Ljava/lang/Object;)Z
 move-result v0
 if-nez v0,:current_owner
 return-void
 :current_owner
 invoke-virtual {p2},Landroid/content/Intent;->getAction()Ljava/lang/String;
 move-result-object v0
 const-string v1,"android.intent.action.telechips.carplay.over.wireless.connected"
 invoke-virtual {v1,v0},Ljava/lang/String;->equals(Ljava/lang/Object;)Z
 move-result v1
 if-eqz v1,:disconnected
 const/4 v1,1
 iget-object v0,p0,Lorg/ev6/research/TransportReceiver;->service:Lorg/ev6/research/ProbeService;
 invoke-static {v0,v1},Lorg/ev6/research/BackendCallbacks;->session(Landroid/content/Context;Z)V
 return-void
 :disconnected
 const-string v1,"android.intent.action.telechips.carplay.over.wireless.disconnected"
 invoke-virtual {v1,v0},Ljava/lang/String;->equals(Ljava/lang/Object;)Z
 move-result v1
 if-eqz v1,:transport
 const/4 v1,0
 iget-object v0,p0,Lorg/ev6/research/TransportReceiver;->service:Lorg/ev6/research/ProbeService;
 invoke-static {v0,v1},Lorg/ev6/research/BackendCallbacks;->session(Landroid/content/Context;Z)V
 return-void
 :transport
 const-string v1,"android.hardware.bt.action.IAP2_BT_DEVICE_ATTACHED_FOR_CARPLAY"
 invoke-virtual {v1,v0},Ljava/lang/String;->equals(Ljava/lang/Object;)Z
 move-result v0
 iget-object v1,p0,Lorg/ev6/research/TransportReceiver;->service:Lorg/ev6/research/ProbeService;
 invoke-virtual {v1,v0},Lorg/ev6/research/ProbeService;->transport(Z)V
 return-void
.end method
