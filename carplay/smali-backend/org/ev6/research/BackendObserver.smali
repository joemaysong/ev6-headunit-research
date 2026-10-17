.class public Lorg/ev6/research/BackendObserver;
.super Lcom/tcc/iap2/IIap2Listener$Stub;
.field private trace:Lorg/ev6/research/LinkTrace;
.field private host:Lorg/ev6/research/ProbeService;
.method public constructor <init>(Lorg/ev6/research/ProbeService;)V
 .locals 2
 invoke-direct {p0},Lcom/tcc/iap2/IIap2Listener$Stub;-><init>()V
 iput-object p1,p0,Lorg/ev6/research/BackendObserver;->host:Lorg/ev6/research/ProbeService;
 new-instance v0,Lorg/ev6/research/LinkTrace;
 const-string v1,"TRACE_TX_CSM=0x"
 invoke-direct {v0,p1,v1},Lorg/ev6/research/LinkTrace;-><init>(Landroid/content/Context;Ljava/lang/String;)V
 iput-object v0,p0,Lorg/ev6/research/BackendObserver;->trace:Lorg/ev6/research/LinkTrace;
 return-void
.end method
.method public connected(ILjava/lang/String;)V
 .locals 1
 const-string v0,"OBSERVER_CONNECTED"
 invoke-static {v0},Lorg/ev6/research/ProbeService;->mark(Ljava/lang/String;)V
 return-void
.end method
.method public disconnected(I)V
 .locals 1
 const-string v0,"OBSERVER_DISCONNECTED"
 invoke-static {v0},Lorg/ev6/research/ProbeService;->mark(Ljava/lang/String;)V
 return-void
.end method
.method public notify(IIILcom/tcc/iap2/Iap2NotiMsg;)V
 .locals 6
 iget-object v0,p0,Lorg/ev6/research/BackendObserver;->host:Lorg/ev6/research/ProbeService;
 invoke-static {v0},Lorg/ev6/research/BackendOwnership;->current(Ljava/lang/Object;)Z
 move-result v0
 if-nez v0,:current_owner
 return-void
 :current_owner
 # Only log event IDs. Protocol bodies are never printed or persisted.
 const/16 v0,80
 if-eq p1,v0,:trace_packet
 invoke-static {p1},Ljava/lang/Integer;->toString(I)Ljava/lang/String;
 move-result-object v0
 const-string v1,"OBSERVER_EVENT="
 invoke-virtual {v1,v0},Ljava/lang/String;->concat(Ljava/lang/String;)Ljava/lang/String;
 move-result-object v0
 invoke-static {v0},Lorg/ev6/research/ProbeService;->mark(Ljava/lang/String;)V
 :trace_packet
 const/16 v0,80
 if-ne p1,v0,:events
 if-eqz p4,:events
 :tx_start
 invoke-virtual {p4},Lcom/tcc/iap2/Iap2NotiMsg;->getItemGroup()Lcom/tcc/iap2/Iap2NotiMsg$Iap2ObjGroup;
 move-result-object v0
 const/4 v1,0
 invoke-virtual {v0},Lcom/tcc/iap2/Iap2NotiMsg$Iap2ObjGroup;->getItemCnt()I
 move-result v3
 :tx_items
 if-ge v1,v3,:tx_end
 invoke-virtual {v0,v1},Lcom/tcc/iap2/Iap2NotiMsg$Iap2ObjGroup;->getObjItem(I)Lcom/tcc/iap2/Iap2NotiMsg$Iap2ObjItem;
 move-result-object v2
 invoke-virtual {v2},Lcom/tcc/iap2/Iap2NotiMsg$Iap2ObjItem;->getItemId()I
 move-result v4
 if-nez v4,:tx_next
 invoke-virtual {v2},Lcom/tcc/iap2/Iap2NotiMsg$Iap2ObjItem;->getItemParamType()I
 move-result v4
 const/16 v5,11
 if-ne v4,v5,:tx_next
 invoke-virtual {v2},Lcom/tcc/iap2/Iap2NotiMsg$Iap2ObjItem;->getItemByteArray()[B
 move-result-object v2
 if-eqz v2,:tx_next
 array-length v4,v2
 iget-object v5,p0,Lorg/ev6/research/BackendObserver;->trace:Lorg/ev6/research/LinkTrace;
 invoke-virtual {v5,v2,v4},Lorg/ev6/research/LinkTrace;->feed([BI)V
 :tx_next
 add-int/lit8 v1,v1,1
 goto :tx_items
 :tx_end
 goto :events
 .catch Ljava/lang/Exception; {:tx_start .. :tx_end} :tx_error
 :tx_error
 move-exception v0
 const-string v0,"TRACE_TX_UNAVAILABLE"
 invoke-static {v0},Lorg/ev6/research/ProbeService;->mark(Ljava/lang/String;)V
 :events
 # 24 is DeviceTransportIdentifier, NOT user consent. Native alone replies
 # to WifiRequestAccessoryInformation (131 -> MsgConsumer 12).
 iget-object v0,p0,Lorg/ev6/research/BackendObserver;->host:Lorg/ev6/research/ProbeService;
 invoke-static {v0,p1,p4},Lorg/ev6/research/PairingEvents;->eventForOwner(Landroid/content/Context;ILcom/tcc/iap2/Iap2NotiMsg;)V
 return-void
.end method
