.class public Lorg/ev6/research/LinkTrace;
.super Ljava/lang/Object;
.field private bytes:[B
.field private size:I
.field private direction:Ljava/lang/String;
.field private owner:Landroid/content/Context;
.field public count:I
.method public constructor <init>(Landroid/content/Context;Ljava/lang/String;)V
 .locals 1
 invoke-direct {p0},Ljava/lang/Object;-><init>()V
 const v0,65535
 new-array v0,v0,[B
 iput-object v0,p0,Lorg/ev6/research/LinkTrace;->bytes:[B
 iput-object p1,p0,Lorg/ev6/research/LinkTrace;->owner:Landroid/content/Context;
 iput-object p2,p0,Lorg/ev6/research/LinkTrace;->direction:Ljava/lang/String;
 return-void
.end method
.method private static u16([BI)I
 .locals 2
 aget-byte v0,p0,p1
 and-int/lit16 v0,v0,255
 shl-int/lit8 v0,v0,8
 add-int/lit8 p1,p1,1
 aget-byte v1,p0,p1
 and-int/lit16 v1,v1,255
 or-int/2addr v0,v1
 return v0
.end method
.method private static checksum([BII)Z
 .locals 2
 const/4 v0,0
 :loop
 if-ge p1,p2,:done
 aget-byte v1,p0,p1
 add-int/2addr v0,v1
 add-int/lit8 p1,p1,1
 goto :loop
 :done
 and-int/lit16 v0,v0,255
 if-nez v0,:bad
 const/4 v0,1
 return v0
 :bad
 const/4 v0,0
 return v0
.end method
.method private feedUnsafe([BI)V
 .locals 9
 if-eqz p1,:return
 array-length v0,p1
 if-gt p2,v0,:return
 if-ltz p2,:return
 const/4 v0,0
 :loop
 if-ge v0,p2,:return
 iget-object v1,p0,Lorg/ev6/research/LinkTrace;->bytes:[B
 iget v2,p0,Lorg/ev6/research/LinkTrace;->size:I
 aget-byte v3,p1,v0
 add-int/lit8 v0,v0,1
 if-nez v2,:second
 const/4 v4,-1
 if-ne v3,v4,:loop
 :second
 const/4 v4,1
 if-ne v2,v4,:store
 const/16 v4,0x5a
 if-eq v3,v4,:store
 const/4 v4,-1
 if-eq v3,v4,:loop
 const/4 v2,0
 iput v2,p0,Lorg/ev6/research/LinkTrace;->size:I
 goto :loop
 :store
 aput-byte v3,v1,v2
 add-int/lit8 v2,v2,1
 iput v2,p0,Lorg/ev6/research/LinkTrace;->size:I
 const/16 v4,9
 if-lt v2,v4,:loop
 const/4 v3,0
 invoke-static {v1,v3,v4},Lorg/ev6/research/LinkTrace;->checksum([BII)Z
 move-result v3
 if-eqz v3,:reset
 const/4 v3,2
 invoke-static {v1,v3},Lorg/ev6/research/LinkTrace;->u16([BI)I
 move-result v5
 if-lt v5,v4,:reset
 if-lt v2,v5,:loop
 if-ne v2,v5,:reset
 if-eq v5,v4,:reset
 invoke-static {v1,v4,v5},Lorg/ev6/research/LinkTrace;->checksum([BII)Z
 move-result v3
 if-eqz v3,:reset
 const/4 v3,4
 aget-byte v3,v1,v3
 and-int/lit16 v3,v3,191
 if-nez v3,:reset
 const/16 v3,16
 if-lt v5,v3,:reset
 const/16 v6,9
 :csm
 add-int/lit8 v3,v6,6
 add-int/lit8 v7,v5,-1
 if-gt v3,v7,:reset
 invoke-static {v1,v6},Lorg/ev6/research/LinkTrace;->u16([BI)I
 move-result v3
 const/16 v4,0x4040
 if-ne v3,v4,:incomplete
 add-int/lit8 v3,v6,2
 invoke-static {v1,v3},Lorg/ev6/research/LinkTrace;->u16([BI)I
 move-result v3
 const/4 v4,6
 if-lt v3,v4,:incomplete
 add-int v4,v6,v3
 if-gt v4,v7,:incomplete
 add-int/lit8 v3,v6,4
 invoke-static {v1,v3},Lorg/ev6/research/LinkTrace;->u16([BI)I
 move-result v3
 iget-object v7,p0,Lorg/ev6/research/LinkTrace;->direction:Ljava/lang/String;
 iget-object v8,p0,Lorg/ev6/research/LinkTrace;->owner:Landroid/content/Context;
 invoke-static {v8,v7,v3},Lorg/ev6/research/PairingEvents;->csmForOwner(Landroid/content/Context;Ljava/lang/String;I)V
 const-string v8,"TRACE_TX_CSM=0x"
 invoke-virtual {v8,v7},Ljava/lang/String;->equals(Ljava/lang/Object;)Z
 move-result v8
 if-eqz v8,:skip_audit
 iget-object v8,p0,Lorg/ev6/research/LinkTrace;->owner:Landroid/content/Context;
 invoke-static {v8,v3,v1,v6,v4},Lorg/ev6/research/WifiReplyAudit;->ownerMessage(Landroid/content/Context;I[BII)V
 :skip_audit
 add-int/lit8 v7,v5,-1
 invoke-static {v3},Ljava/lang/Integer;->toHexString(I)Ljava/lang/String;
 move-result-object v3
 iget-object v6,p0,Lorg/ev6/research/LinkTrace;->direction:Ljava/lang/String;
 invoke-virtual {v6,v3},Ljava/lang/String;->concat(Ljava/lang/String;)Ljava/lang/String;
 move-result-object v3
 invoke-static {v3},Lorg/ev6/research/ProbeService;->mark(Ljava/lang/String;)V
 iget v3,p0,Lorg/ev6/research/LinkTrace;->count:I
 add-int/lit8 v3,v3,1
 iput v3,p0,Lorg/ev6/research/LinkTrace;->count:I
 move v6,v4
 if-lt v6,v7,:csm
 goto :reset
 :incomplete
 const-string v3,"TRACE_CSM_INCOMPLETE_OR_CONTINUATION"
 invoke-static {v3},Lorg/ev6/research/ProbeService;->mark(Ljava/lang/String;)V
 :reset
 const/4 v2,0
 iput v2,p0,Lorg/ev6/research/LinkTrace;->size:I
 goto :loop
 :return
 return-void
.end method

.method public feed([BI)V
 .locals 1
 monitor-enter p0
 :start
 invoke-direct {p0,p1,p2},Lorg/ev6/research/LinkTrace;->feedUnsafe([BI)V
 :end
 monitor-exit p0
 return-void
 .catchall {:start .. :end} :error
 :error
 move-exception v0
 monitor-exit p0
 throw v0
.end method
