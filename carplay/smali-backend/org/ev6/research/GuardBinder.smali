.class public Lorg/ev6/research/GuardBinder;
.super Landroid/os/Binder;
.field private trace:Lorg/ev6/research/LinkTrace;
.field private readCode:I
.field private target:Landroid/os/IBinder;
.field private owner:Lorg/ev6/research/ProbeService;
.method public constructor <init>(Lorg/ev6/research/ProbeService;Landroid/os/IBinder;)V
 .locals 3
 invoke-direct {p0},Landroid/os/Binder;-><init>()V
 iput-object p1,p0,Lorg/ev6/research/GuardBinder;->owner:Lorg/ev6/research/ProbeService;
 iput-object p2,p0,Lorg/ev6/research/GuardBinder;->target:Landroid/os/IBinder;
 const/4 v0,0
 const-string v1,"com.tcc.iap2.IIap2Service"
 invoke-virtual {p0,v0,v1},Landroid/os/Binder;->attachInterface(Landroid/os/IInterface;Ljava/lang/String;)V
 new-instance v0,Lorg/ev6/research/LinkTrace;
 const-string v1,"TRACE_RX_CSM=0x"
 invoke-direct {v0,p1,v1},Lorg/ev6/research/LinkTrace;-><init>(Landroid/content/Context;Ljava/lang/String;)V
 iput-object v0,p0,Lorg/ev6/research/GuardBinder;->trace:Lorg/ev6/research/LinkTrace;
 :start
 const-string v0,"com.tcc.iap2.IIap2Service$Stub"
 invoke-static {v0},Ljava/lang/Class;->forName(Ljava/lang/String;)Ljava/lang/Class;
 move-result-object v0
 const-string v1,"TRANSACTION_Iap2SvcTransportReadDone"
 invoke-virtual {v0,v1},Ljava/lang/Class;->getDeclaredField(Ljava/lang/String;)Ljava/lang/reflect/Field;
 move-result-object v0
 const/4 v1,1
 invoke-virtual {v0,v1},Ljava/lang/reflect/Field;->setAccessible(Z)V
 const/4 v1,0
 invoke-virtual {v0,v1},Ljava/lang/reflect/Field;->getInt(Ljava/lang/Object;)I
 move-result v0
 iput v0,p0,Lorg/ev6/research/GuardBinder;->readCode:I
 const-string v0,"TRACE_TRANSACTION_RESOLVED"
 invoke-static {v0},Lorg/ev6/research/ProbeService;->mark(Ljava/lang/String;)V
 :end
 return-void
 .catch Ljava/lang/Exception; {:start .. :end} :error
 :error
 move-exception v0
 const-string v0,"TRACE_TRANSACTION_UNAVAILABLE"
 invoke-static {v0},Lorg/ev6/research/ProbeService;->mark(Ljava/lang/String;)V
 return-void
.end method
.method protected onTransact(ILandroid/os/Parcel;Landroid/os/Parcel;I)Z
 .locals 4
 iget-object v0,p0,Lorg/ev6/research/GuardBinder;->owner:Lorg/ev6/research/ProbeService;
 invoke-static {v0},Lorg/ev6/research/BackendOwnership;->current(Ljava/lang/Object;)Z
 move-result v0
 if-nez v0,:current_owner
 new-instance v0,Landroid/os/DeadObjectException;
 invoke-direct {v0},Landroid/os/DeadObjectException;-><init>()V
 throw v0
 :current_owner
 invoke-static {},Landroid/os/Binder;->getCallingUid()I
 move-result v0
 if-eqz v0,:allow
 const/16 v1,1000
 if-eq v0,v1,:allow
 invoke-static {},Landroid/os/Process;->myUid()I
 move-result v1
 if-eq v0,v1,:allow
 new-instance v0,Ljava/lang/SecurityException;
 const-string v1,"Only system and this diagnostic process may call the backend"
 invoke-direct {v0,v1},Ljava/lang/SecurityException;-><init>(Ljava/lang/String;)V
 throw v0
 :allow
 iget v0,p0,Lorg/ev6/research/GuardBinder;->readCode:I
 if-ne p1,v0,:forward
 const/4 v0,0
 :parse_start
 invoke-static {},Landroid/os/Parcel;->obtain()Landroid/os/Parcel;
 move-result-object v0
 invoke-virtual {p2},Landroid/os/Parcel;->dataSize()I
 move-result v1
 const/4 v2,0
 invoke-virtual {v0,p2,v2,v1},Landroid/os/Parcel;->appendFrom(Landroid/os/Parcel;II)V
 invoke-virtual {v0,v2},Landroid/os/Parcel;->setDataPosition(I)V
 const-string v1,"com.tcc.iap2.IIap2Service"
 invoke-virtual {v0,v1},Landroid/os/Parcel;->enforceInterface(Ljava/lang/String;)V
 invoke-virtual {v0},Landroid/os/Parcel;->createByteArray()[B
 move-result-object v1
 invoke-virtual {v0},Landroid/os/Parcel;->readInt()I
 move-result v2
 iget-object v3,p0,Lorg/ev6/research/GuardBinder;->trace:Lorg/ev6/research/LinkTrace;
 invoke-virtual {v3,v1,v2},Lorg/ev6/research/LinkTrace;->feed([BI)V
 :parse_end
 invoke-virtual {v0},Landroid/os/Parcel;->recycle()V
 goto :forward
 .catch Ljava/lang/Exception; {:parse_start .. :parse_end} :parse_error
 :parse_error
 move-exception v1
 if-eqz v0,:forward
 invoke-virtual {v0},Landroid/os/Parcel;->recycle()V
 :forward
 iget-object v0,p0,Lorg/ev6/research/GuardBinder;->target:Landroid/os/IBinder;
 invoke-interface {v0,p1,p2,p3,p4},Landroid/os/IBinder;->transact(ILandroid/os/Parcel;Landroid/os/Parcel;I)Z
 move-result v0
 return v0
.end method
