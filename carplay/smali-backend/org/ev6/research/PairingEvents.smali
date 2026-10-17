.class public Lorg/ev6/research/PairingEvents;
.super Ljava/lang/Object;
# Only metadata. No identifier, credentials, protocol body, or UI-success claim.
.field private static context:Landroid/content/Context;
.field private static epoch:I
.field private static authenticated:Z
.field private static identified:Z
.field private static transport:Z
.field private static status:I = -1
.field private static request:Z
.field private static reply:Z
.method public static declared-synchronized init(Landroid/content/Context;)V
 .locals 0
 sput-object p0,Lorg/ev6/research/PairingEvents;->context:Landroid/content/Context;
 invoke-static {},Lorg/ev6/research/PairingEvents;->reset()V
 return-void
.end method
.method public static declared-synchronized reset()V
 .locals 1
 sget v0,Lorg/ev6/research/PairingEvents;->epoch:I
 add-int/lit8 v0,v0,1
 sput v0,Lorg/ev6/research/PairingEvents;->epoch:I
 const/4 v0,0
 sput-boolean v0,Lorg/ev6/research/PairingEvents;->authenticated:Z
 sput-boolean v0,Lorg/ev6/research/PairingEvents;->identified:Z
 sput-boolean v0,Lorg/ev6/research/PairingEvents;->transport:Z
 sput-boolean v0,Lorg/ev6/research/PairingEvents;->request:Z
 sput-boolean v0,Lorg/ev6/research/PairingEvents;->reply:Z
 const/4 v0,-1
 sput v0,Lorg/ev6/research/PairingEvents;->status:I
 invoke-static {},Lorg/ev6/research/PairingEvents;->publish()V
 return-void
.end method
.method public static declared-synchronized release(Landroid/content/Context;)V
 .locals 1
 sget-object v0,Lorg/ev6/research/PairingEvents;->context:Landroid/content/Context;
 if-ne v0,p0,:done
 const/4 v0,0
 sput-object v0,Lorg/ev6/research/PairingEvents;->context:Landroid/content/Context;
 invoke-static {},Lorg/ev6/research/PairingEvents;->reset()V
 :done
 return-void
.end method
.method public static declared-synchronized eventForOwner(Landroid/content/Context;ILcom/tcc/iap2/Iap2NotiMsg;)V
 .locals 1
 sget-object v0,Lorg/ev6/research/PairingEvents;->context:Landroid/content/Context;
 if-ne v0,p0,:done
 invoke-static {p0},Lorg/ev6/research/BackendOwnership;->current(Ljava/lang/Object;)Z
 move-result v0
 if-eqz v0,:done
 invoke-static {p1,p2},Lorg/ev6/research/PairingEvents;->event(ILcom/tcc/iap2/Iap2NotiMsg;)V
 :done
 return-void
.end method
.method public static declared-synchronized resetForOwner(Landroid/content/Context;)V
 .locals 1
 sget-object v0,Lorg/ev6/research/PairingEvents;->context:Landroid/content/Context;
 if-ne v0,p0,:done
 invoke-static {p0},Lorg/ev6/research/BackendOwnership;->current(Ljava/lang/Object;)Z
 move-result v0
 if-eqz v0,:done
 invoke-static {},Lorg/ev6/research/PairingEvents;->reset()V
 :done
 return-void
.end method
.method public static declared-synchronized csmForOwner(Landroid/content/Context;Ljava/lang/String;I)V
 .locals 1
 sget-object v0,Lorg/ev6/research/PairingEvents;->context:Landroid/content/Context;
 if-ne v0,p0,:done
 invoke-static {p0},Lorg/ev6/research/BackendOwnership;->current(Ljava/lang/Object;)Z
 move-result v0
 if-eqz v0,:done
 invoke-static {p1,p2},Lorg/ev6/research/PairingEvents;->csm(Ljava/lang/String;I)V
 :done
 return-void
.end method
.method public static declared-synchronized event(ILcom/tcc/iap2/Iap2NotiMsg;)V
 .locals 6
 const/4 v0,0
 if-eq p0,v0,:identified
 const/4 v0,1
 if-eq p0,v0,:invalid
 const/4 v0,2
 if-eq p0,v0,:invalid
 const/4 v0,4
 if-eq p0,v0,:invalid
 const/4 v0,7
 if-eq p0,v0,:invalid
 const/4 v0,5
 if-eq p0,v0,:authenticated
 const/16 v0,24
 if-eq p0,v0,:transport
 const/16 v0,29
 if-ne p0,v0,:done
 # Same param 0 / OBJ_ENUM (8) / first int as OEM WirelessCarPlay.
 # Malformed or missing status fails closed instead of retaining status=1.
 const/4 v0,-1
 sput v0,Lorg/ev6/research/PairingEvents;->status:I
 :parse_start
 if-eqz p1,:publish
 invoke-virtual {p1},Lcom/tcc/iap2/Iap2NotiMsg;->getItemGroup()Lcom/tcc/iap2/Iap2NotiMsg$Iap2ObjGroup;
 move-result-object v0
 invoke-virtual {v0},Lcom/tcc/iap2/Iap2NotiMsg$Iap2ObjGroup;->getItemCnt()I
 move-result v1
 const/4 v2,0
 :items
 if-ge v2,v1,:publish
 invoke-virtual {v0,v2},Lcom/tcc/iap2/Iap2NotiMsg$Iap2ObjGroup;->getObjItem(I)Lcom/tcc/iap2/Iap2NotiMsg$Iap2ObjItem;
 move-result-object v3
 invoke-virtual {v3},Lcom/tcc/iap2/Iap2NotiMsg$Iap2ObjItem;->getItemId()I
 move-result v4
 if-nez v4,:next
 invoke-virtual {v3},Lcom/tcc/iap2/Iap2NotiMsg$Iap2ObjItem;->getItemParamType()I
 move-result v4
 const/16 v5,8
 if-ne v4,v5,:next
 invoke-virtual {v3},Lcom/tcc/iap2/Iap2NotiMsg$Iap2ObjItem;->getItemIntArray()[I
 move-result-object v3
 if-eqz v3,:publish
 array-length v4,v3
 if-eqz v4,:publish
 const/4 v4,0
 aget v4,v3,v4
 sput v4,Lorg/ev6/research/PairingEvents;->status:I
 goto :publish
 :next
 add-int/lit8 v2,v2,1
 goto :items
 :parse_end
 .catch Ljava/lang/Exception; {:parse_start .. :parse_end} :parse_error
 :parse_error
 move-exception v0
 goto :publish
 :identified
 const/4 v0,1
 sput-boolean v0,Lorg/ev6/research/PairingEvents;->identified:Z
 goto :publish
 :authenticated
 const/4 v0,1
 sput-boolean v0,Lorg/ev6/research/PairingEvents;->authenticated:Z
 goto :publish
 :transport
 const/4 v0,1
 sput-boolean v0,Lorg/ev6/research/PairingEvents;->transport:Z
 goto :publish
 :invalid
 invoke-static {},Lorg/ev6/research/PairingEvents;->reset()V
 return-void
 :publish
 invoke-static {},Lorg/ev6/research/PairingEvents;->publish()V
 :done
 return-void
.end method
.method public static declared-synchronized csm(Ljava/lang/String;I)V
 .locals 2
 const-string v0,"TRACE_RX_CSM=0x"
 invoke-virtual {v0,p0},Ljava/lang/String;->equals(Ljava/lang/Object;)Z
 move-result v0
 if-eqz v0,:tx
 const/16 v0,0x5702
 if-ne p1,v0,:done
 const/4 v0,1
 sput-boolean v0,Lorg/ev6/research/PairingEvents;->request:Z
 goto :publish
 :tx
 const-string v0,"TRACE_TX_CSM=0x"
 invoke-virtual {v0,p0},Ljava/lang/String;->equals(Ljava/lang/Object;)Z
 move-result v0
 if-eqz v0,:done
 const/16 v0,0x5703
 if-ne p1,v0,:done
 const/4 v0,1
 sput-boolean v0,Lorg/ev6/research/PairingEvents;->reply:Z
 :publish
 invoke-static {},Lorg/ev6/research/PairingEvents;->publish()V
 :done
 return-void
.end method
.method private static publish()V
 .locals 7
 sget-object v0,Lorg/ev6/research/PairingEvents;->context:Landroid/content/Context;
 if-eqz v0,:done
 new-instance v1,Ljava/lang/StringBuilder;
 invoke-direct {v1},Ljava/lang/StringBuilder;-><init>()V
 const-string v2,"epoch="
 invoke-virtual {v1,v2},Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
 sget v2,Lorg/ev6/research/PairingEvents;->epoch:I
 invoke-virtual {v1,v2},Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
 const-string v2," auth="
 invoke-virtual {v1,v2},Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
 sget-boolean v2,Lorg/ev6/research/PairingEvents;->authenticated:Z
 invoke-virtual {v1,v2},Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;
 const-string v2," identified="
 invoke-virtual {v1,v2},Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
 sget-boolean v2,Lorg/ev6/research/PairingEvents;->identified:Z
 invoke-virtual {v1,v2},Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;
 const-string v2," transport="
 invoke-virtual {v1,v2},Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
 sget-boolean v2,Lorg/ev6/research/PairingEvents;->transport:Z
 invoke-virtual {v1,v2},Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;
 const-string v2," status="
 invoke-virtual {v1,v2},Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
 sget v2,Lorg/ev6/research/PairingEvents;->status:I
 invoke-virtual {v1,v2},Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
 const-string v2," request="
 invoke-virtual {v1,v2},Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
 sget-boolean v2,Lorg/ev6/research/PairingEvents;->request:Z
 invoke-virtual {v1,v2},Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;
 const-string v2," reply="
 invoke-virtual {v1,v2},Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
 sget-boolean v2,Lorg/ev6/research/PairingEvents;->reply:Z
 invoke-virtual {v1,v2},Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;
 invoke-virtual {v1},Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
 move-result-object v1
 invoke-static {v0,v1},Lorg/ev6/research/BackendCallbacks;->pairing(Landroid/content/Context;Ljava/lang/String;)V
 :done
 return-void
.end method
