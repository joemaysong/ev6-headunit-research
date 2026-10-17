.class public Lorg/ev6/research/ProbeService;
.super Landroid/app/Service;
.field private wifiReceiver:Lorg/ev6/research/WifiEvents;
.field private delegate:Ljava/lang/Object;
.field private enteredCreate:Z
.field private receiver:Lorg/ev6/research/TransportReceiver;
.method public constructor <init>()V
 .locals 0
 invoke-direct {p0},Landroid/app/Service;-><init>()V
 return-void
.end method
.method public static mark(Ljava/lang/String;)V
 .locals 1
 const-string v0,"EV6BackendProbe"
 invoke-static {v0,p0},Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
 return-void
.end method
.method public onCreate()V
 .locals 7
 invoke-super {p0},Landroid/app/Service;->onCreate()V
 :try_start
 invoke-static {p0},Lorg/ev6/research/BackendOwnership;->claim(Ljava/lang/Object;)V
 invoke-static {p0},Lorg/ev6/research/PairingEvents;->init(Landroid/content/Context;)V
 invoke-static {p0},Lorg/ev6/research/WifiReplyAudit;->init(Landroid/content/Context;)V
 invoke-static {p0},Lorg/ev6/research/ControlFiles;->init(Landroid/content/Context;)V
 invoke-static {p0},Lorg/ev6/research/NativeTransportGate;->init(Landroid/content/Context;)V
 invoke-static {p0},Lorg/ev6/research/BackendRuntime;->init(Landroid/content/Context;)V
 invoke-direct {p0},Lorg/ev6/research/ProbeService;->registerWifiEvents()V
 const-string v0,"PROBE_STARTED_NO_TRANSPORT"
 invoke-static {v0},Lorg/ev6/research/ProbeService;->mark(Ljava/lang/String;)V
 const-string v0,"oem"
 const/4 v1,0
 invoke-virtual {p0,v0,v1},Landroid/content/ContextWrapper;->getDir(Ljava/lang/String;I)Ljava/io/File;
 move-result-object v0
 invoke-virtual {v0},Ljava/io/File;->getAbsolutePath()Ljava/lang/String;
 move-result-object v2
 new-instance v0,Ldalvik/system/DexClassLoader;
 const-string v1,"/system/app/Iap2ServerApp.apk"
 const-string v3,"/system/lib"
 invoke-virtual {p0},Landroid/content/ContextWrapper;->getClassLoader()Ljava/lang/ClassLoader;
 move-result-object v4
 invoke-direct {v0,v1,v2,v3,v4},Ldalvik/system/DexClassLoader;-><init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/ClassLoader;)V
 const-string v1,"com.tcc.iap2server.Iap2Service"
 invoke-virtual {v0,v1},Ljava/lang/ClassLoader;->loadClass(Ljava/lang/String;)Ljava/lang/Class;
 move-result-object v0
 const-string v1,"OEM_CLASS_LOADED"
 invoke-static {v1},Lorg/ev6/research/ProbeService;->mark(Ljava/lang/String;)V
 invoke-virtual {v0},Ljava/lang/Class;->newInstance()Ljava/lang/Object;
 move-result-object v0
 iput-object v0,p0,Lorg/ev6/research/ProbeService;->delegate:Ljava/lang/Object;
 const-string v1,"OEM_INSTANCE_CREATED"
 invoke-static {v1},Lorg/ev6/research/ProbeService;->mark(Ljava/lang/String;)V
 const/4 v2,1
 new-array v2,v2,[Ljava/lang/Object;
 const/4 v3,0
 aput-object p0,v2,v3
 const-string v1,"attachBaseContext"
 invoke-static {v0,v1,v2},Lorg/ev6/research/Reflect;->call(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;
 const-string v1,"OEM_CONTEXT_ATTACHED"
 invoke-static {v1},Lorg/ev6/research/ProbeService;->mark(Ljava/lang/String;)V
 const/4 v1,1
 iput-boolean v1,p0,Lorg/ev6/research/ProbeService;->enteredCreate:Z
 const/4 v1,0
 new-array v2,v1,[Ljava/lang/Object;
 const-string v1,"onCreate"
 invoke-static {v0,v1,v2},Lorg/ev6/research/Reflect;->call(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;
 const-string v1,"OEM_SERVICE_CREATED_NO_TRANSPORT"
 invoke-static {v1},Lorg/ev6/research/ProbeService;->mark(Ljava/lang/String;)V
 # Configure the isolated instance only. Does not invoke native start.
 const/4 v1,1
 new-array v2,v1,[Ljava/lang/Object;
 const/4 v3,4
 invoke-static {v3},Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
 move-result-object v3
 const/4 v4,0
 aput-object v3,v2,v4
 const-string v1,"setComponent"
 invoke-static {v0,v1,v2},Lorg/ev6/research/Reflect;->call(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;

 invoke-static {v0},Lorg/ev6/research/WifiCapability;->enable(Ljava/lang/Object;)V
 const-string v1,"BT_COMPONENT_CONFIGURED_NO_TRANSPORT"
 invoke-static {v1},Lorg/ev6/research/ProbeService;->mark(Ljava/lang/String;)V
 new-instance v3,Landroid/content/Intent;
 invoke-direct {v3},Landroid/content/Intent;-><init>()V
 aput-object v3,v2,v4
 const-string v1,"onBind"
 invoke-static {v0,v1,v2},Lorg/ev6/research/Reflect;->call(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;
 move-result-object v3
 check-cast v3,Landroid/os/IBinder;
 invoke-interface {v3},Landroid/os/IBinder;->getInterfaceDescriptor()Ljava/lang/String;
 move-result-object v4
 const-string v1,"EV6BackendProbe"
 invoke-static {v1,v4},Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
 invoke-static {v3},Lcom/tcc/iap2/IIap2Service$Stub;->asInterface(Landroid/os/IBinder;)Lcom/tcc/iap2/IIap2Service;
 move-result-object v3
 invoke-interface {v3},Lcom/tcc/iap2/IIap2Service;->Iap2SvcGetState()I
 move-result v4
 invoke-static {v4},Ljava/lang/Integer;->toString(I)Ljava/lang/String;
 move-result-object v4
 const-string v1,"OEM_BINDER_STATE="
 invoke-virtual {v1,v4},Ljava/lang/String;->concat(Ljava/lang/String;)Ljava/lang/String;
 move-result-object v1
 invoke-static {v1},Lorg/ev6/research/ProbeService;->mark(Ljava/lang/String;)V
 const/4 v1,0
 new-array v2,v1,[Ljava/lang/Object;
 const-string v1,"getComponent"
 invoke-static {v0,v1,v2},Lorg/ev6/research/Reflect;->call(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;
 move-result-object v3
 invoke-virtual {v3},Ljava/lang/Object;->toString()Ljava/lang/String;
 move-result-object v3
 const-string v1,"OEM_COMPONENT="
 invoke-virtual {v1,v3},Ljava/lang/String;->concat(Ljava/lang/String;)Ljava/lang/String;
 move-result-object v1
 invoke-static {v1},Lorg/ev6/research/ProbeService;->mark(Ljava/lang/String;)V
 new-instance v1,Landroid/content/Intent;
 invoke-direct {v1},Landroid/content/Intent;-><init>()V
 const/4 v2,1
 new-array v2,v2,[Ljava/lang/Object;
 const/4 v3,0
 aput-object v1,v2,v3
 const-string v1,"onBind"
 invoke-static {v0,v1,v2},Lorg/ev6/research/Reflect;->call(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;
 move-result-object v1
 check-cast v1,Landroid/os/IBinder;
 invoke-static {v1},Lcom/tcc/iap2/IIap2Service$Stub;->asInterface(Landroid/os/IBinder;)Lcom/tcc/iap2/IIap2Service;
 move-result-object v1
 new-instance v2,Lorg/ev6/research/BackendObserver;
 invoke-direct {v2,p0},Lorg/ev6/research/BackendObserver;-><init>(Lorg/ev6/research/ProbeService;)V
 new-instance v3,Lcom/tcc/iap2/Iap2ListenerInfo;
 invoke-direct {v3},Lcom/tcc/iap2/Iap2ListenerInfo;-><init>()V
 const-string v4,"EV6_Backend_Diagnostic"
 invoke-virtual {v3,v4},Lcom/tcc/iap2/Iap2ListenerInfo;->onSetListenerName(Ljava/lang/String;)V
 const v4,0x4cad
 invoke-virtual {v3,v4},Lcom/tcc/iap2/Iap2ListenerInfo;->onSetSupportType(I)V
 invoke-interface {v1,v2,v3},Lcom/tcc/iap2/IIap2Service;->Iap2SvcInit(Lcom/tcc/iap2/IIap2Listener;Lcom/tcc/iap2/Iap2ListenerInfo;)Z
 move-result v3
 const-string v1,"OBSERVER_REGISTERED_WAITING_FOR_DIPO"
 invoke-static {v1},Lorg/ev6/research/ProbeService;->mark(Ljava/lang/String;)V
 # Remove USB event subscription from this isolated backend.
 invoke-virtual {v0},Ljava/lang/Object;->getClass()Ljava/lang/Class;
 move-result-object v1
 const-string v2,"mIap2ServiceReceiver"
 invoke-virtual {v1,v2},Ljava/lang/Class;->getDeclaredField(Ljava/lang/String;)Ljava/lang/reflect/Field;
 move-result-object v1
 const/4 v2,1
 invoke-virtual {v1,v2},Ljava/lang/reflect/Field;->setAccessible(Z)V
 invoke-virtual {v1,v0},Ljava/lang/reflect/Field;->get(Ljava/lang/Object;)Ljava/lang/Object;
 move-result-object v1
 check-cast v1,Landroid/content/BroadcastReceiver;
 invoke-virtual {p0,v1},Landroid/content/ContextWrapper;->unregisterReceiver(Landroid/content/BroadcastReceiver;)V
 new-instance v1,Lorg/ev6/research/TransportReceiver;
 invoke-direct {v1,p0},Lorg/ev6/research/TransportReceiver;-><init>(Lorg/ev6/research/ProbeService;)V
 iput-object v1,p0,Lorg/ev6/research/ProbeService;->receiver:Lorg/ev6/research/TransportReceiver;
 new-instance v2,Landroid/content/IntentFilter;
 invoke-direct {v2},Landroid/content/IntentFilter;-><init>()V
 const-string v3,"android.hardware.bt.action.IAP2_BT_DEVICE_ATTACHED_FOR_CARPLAY"
 invoke-virtual {v2,v3},Landroid/content/IntentFilter;->addAction(Ljava/lang/String;)V
 const-string v3,"android.hardware.bt.action.IAP2_BT_DEVICE_DETACHED_FOR_CARPLAY"
 invoke-virtual {v2,v3},Landroid/content/IntentFilter;->addAction(Ljava/lang/String;)V
 const-string v3,"android.intent.action.telechips.carplay.over.wireless.connected"
 invoke-virtual {v2,v3},Landroid/content/IntentFilter;->addAction(Ljava/lang/String;)V
 const-string v3,"android.intent.action.telechips.carplay.over.wireless.disconnected"
 invoke-virtual {v2,v3},Landroid/content/IntentFilter;->addAction(Ljava/lang/String;)V
 const-string v3,"android.permission.WRITE_SECURE_SETTINGS"
 const/4 v4,0
 invoke-virtual {p0,v1,v2,v3,v4},Landroid/content/ContextWrapper;->registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;Ljava/lang/String;Landroid/os/Handler;)Landroid/content/Intent;
 const-string v1,"backend.ready"
 const/4 v2,1
 invoke-static {p0,v1,v2},Lorg/ev6/research/ControlFiles;->write(Landroid/content/Context;Ljava/lang/String;Z)V
 const-string v1,"BACKEND_READY_WAITING_FOR_REAL_BT_ATTACHMENT"
 invoke-static {v1},Lorg/ev6/research/ProbeService;->mark(Ljava/lang/String;)V
 :try_end
 .catch Ljava/lang/Throwable; {:try_start .. :try_end} :error
 return-void
 :error
 move-exception v0
 const-string v1,"EV6BackendProbe"
 const-string v2,"PROBE_FAILED"
 invoke-static {v1,v2,v0},Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I
 iget-object v0,p0,Lorg/ev6/research/ProbeService;->delegate:Ljava/lang/Object;
 iget-object v1,p0,Lorg/ev6/research/ProbeService;->receiver:Lorg/ev6/research/TransportReceiver;
 iget-object v2,p0,Lorg/ev6/research/ProbeService;->wifiReceiver:Lorg/ev6/research/WifiEvents;
 invoke-static {p0,v0,v1,v2},Lorg/ev6/research/BackendCleanup;->destroy(Landroid/content/Context;Ljava/lang/Object;Landroid/content/BroadcastReceiver;Landroid/content/BroadcastReceiver;)V
 invoke-static {p0},Lorg/ev6/research/PairingEvents;->release(Landroid/content/Context;)V
 const/4 v0,0
 iput-object v0,p0,Lorg/ev6/research/ProbeService;->delegate:Ljava/lang/Object;
 iput-object v0,p0,Lorg/ev6/research/ProbeService;->receiver:Lorg/ev6/research/TransportReceiver;
 iput-object v0,p0,Lorg/ev6/research/ProbeService;->wifiReceiver:Lorg/ev6/research/WifiEvents;
 return-void
.end method
.method public onBind(Landroid/content/Intent;)Landroid/os/IBinder;
 .locals 4
 invoke-static {p0},Lorg/ev6/research/BackendOwnership;->current(Ljava/lang/Object;)Z
 move-result v0
 if-eqz v0,:none
 :try_start
 iget-object v0,p0,Lorg/ev6/research/ProbeService;->delegate:Ljava/lang/Object;
 if-eqz v0,:none
 const/4 v1,1
 new-array v1,v1,[Ljava/lang/Object;
 const/4 v2,0
 aput-object p1,v1,v2
 const-string v2,"onBind"
 invoke-static {v0,v2,v1},Lorg/ev6/research/Reflect;->call(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;
 move-result-object v0
 check-cast v0,Landroid/os/IBinder;
 new-instance v1,Lorg/ev6/research/GuardBinder;
 invoke-direct {v1,p0,v0},Lorg/ev6/research/GuardBinder;-><init>(Lorg/ev6/research/ProbeService;Landroid/os/IBinder;)V
 const-string v2,"BACKEND_BINDER_EXPOSED_TO_SYSTEM_CLIENT"
 invoke-static {v2},Lorg/ev6/research/ProbeService;->mark(Ljava/lang/String;)V
 return-object v1
 :try_end
 .catch Ljava/lang/Throwable; {:try_start .. :try_end} :error
 :error
 move-exception v0
 :none
 const/4 v0,0
 return-object v0
.end method
.method public onStartCommand(Landroid/content/Intent;II)I
 .locals 2
 invoke-static {p0},Lorg/ev6/research/BackendOwnership;->current(Ljava/lang/Object;)Z
 move-result v0
 if-eqz v0,:done
 if-eqz p1,:done
 invoke-virtual {p1},Landroid/content/Intent;->getAction()Ljava/lang/String;
 move-result-object v0
 const-string v1,"org.ev6.research.AP_READY"
 invoke-virtual {v1,v0},Ljava/lang/String;->equals(Ljava/lang/Object;)Z
 move-result v1
 if-eqz v1,:check_retry
 invoke-static {p0},Lorg/ev6/research/NativeTransportGate;->ready(Landroid/content/Context;)V
 goto :done
 :check_retry
 const-string v1,"org.ev6.research.RECOVER_MANUAL_IAP2"
 invoke-virtual {v1,v0},Ljava/lang/String;->equals(Ljava/lang/Object;)Z
 move-result v1
 if-eqz v1,:check_reconcile
 iget-object v0,p0,Lorg/ev6/research/ProbeService;->delegate:Ljava/lang/Object;
 invoke-static {p0,v0,p1},Lorg/ev6/research/NativeTransportGate;->recoverManual(Landroid/content/Context;Ljava/lang/Object;Landroid/content/Intent;)V
 goto :done
 :check_reconcile
 const-string v1,"org.ev6.research.RECONCILE_INACTIVE_RECEIVER"
 invoke-virtual {v1,v0},Ljava/lang/String;->equals(Ljava/lang/Object;)Z
 move-result v1
 if-eqz v1,:check_wifi_retry
 invoke-static {p0},Lorg/ev6/research/SessionMetadata;->reconcile(Landroid/content/Context;)V
 goto :done
 :check_wifi_retry
 const-string v1,"org.ev6.research.RETRY_WIFI"
 invoke-virtual {v1,v0},Ljava/lang/String;->equals(Ljava/lang/Object;)Z
 move-result v0
 if-eqz v0,:done
 const-string v0,"MANUAL_WIFI_OFFER_DISABLED_WAIT_FOR_IPHONE_REQUEST"
 invoke-static {v0},Lorg/ev6/research/ProbeService;->mark(Ljava/lang/String;)V
 :done
 const/4 v0,1
 return v0
.end method
.method public onDestroy()V
 .locals 4
 iget-object v0,p0,Lorg/ev6/research/ProbeService;->delegate:Ljava/lang/Object;
 iget-object v1,p0,Lorg/ev6/research/ProbeService;->receiver:Lorg/ev6/research/TransportReceiver;
 iget-object v2,p0,Lorg/ev6/research/ProbeService;->wifiReceiver:Lorg/ev6/research/WifiEvents;
 invoke-static {p0,v0,v1,v2},Lorg/ev6/research/BackendCleanup;->destroy(Landroid/content/Context;Ljava/lang/Object;Landroid/content/BroadcastReceiver;Landroid/content/BroadcastReceiver;)V
 invoke-static {p0},Lorg/ev6/research/PairingEvents;->release(Landroid/content/Context;)V
 const/4 v0,0
 iput-object v0,p0,Lorg/ev6/research/ProbeService;->delegate:Ljava/lang/Object;
 iput-object v0,p0,Lorg/ev6/research/ProbeService;->receiver:Lorg/ev6/research/TransportReceiver;
 iput-object v0,p0,Lorg/ev6/research/ProbeService;->wifiReceiver:Lorg/ev6/research/WifiEvents;
 invoke-super {p0},Landroid/app/Service;->onDestroy()V
 const-string v0,"PROBE_DESTROYED"
 invoke-static {v0},Lorg/ev6/research/ProbeService;->mark(Ljava/lang/String;)V
 return-void
.end method
.method public transport(Z)V
 .locals 0
 invoke-static {p0,p1},Lorg/ev6/research/NativeTransportGate;->attachment(Landroid/content/Context;Z)V
 return-void
.end method
.method public applyTransport(Z)Z
 .locals 5
 invoke-static {p0},Lorg/ev6/research/BackendOwnership;->current(Ljava/lang/Object;)Z
 move-result v0
 if-eqz v0,:failed
 invoke-static {p0},Lorg/ev6/research/PairingEvents;->resetForOwner(Landroid/content/Context;)V
 :try_start
 iget-object v0,p0,Lorg/ev6/research/ProbeService;->delegate:Ljava/lang/Object;
 if-eqz v0,:failed
 const/4 v1,1
 new-array v1,v1,[Ljava/lang/Object;
 const/4 v2,4
 invoke-static {v2},Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
 move-result-object v2
 const/4 v3,0
 aput-object v2,v1,v3
 if-eqz p1,:stop
 const-string v2,"onStartMain"
 const-string v3,"REAL_BT_ATTACHMENT_NATIVE_START"
 goto :invoke
 :stop
 const-string v2,"onFinishMain"
 const-string v3,"REAL_BT_DETACHMENT_NATIVE_STOP"
 :invoke
 invoke-static {v3},Lorg/ev6/research/ProbeService;->mark(Ljava/lang/String;)V
 invoke-static {v0,v2,v1},Lorg/ev6/research/Reflect;->call(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;
 :try_end
 .catch Ljava/lang/Throwable; {:try_start .. :try_end} :error
 const/4 v0,1
 return v0
 :error
 move-exception v0
 const-string v1,"EV6BackendProbe"
 const-string v2,"TRANSPORT_FAILED"
 invoke-static {v1,v2,v0},Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I
 :failed
 const/4 v0,0
 return v0
.end method


.method private registerWifiEvents()V
 .locals 3
 new-instance v0,Lorg/ev6/research/WifiEvents;
 invoke-direct {v0,p0}, Lorg/ev6/research/WifiEvents;-><init>(Lorg/ev6/research/ProbeService;)V
 iput-object v0,p0,Lorg/ev6/research/ProbeService;->wifiReceiver:Lorg/ev6/research/WifiEvents;
 new-instance v1,Landroid/content/IntentFilter;
 const-string v2,"android.net.wifi.WIFI_STATE_CHANGED"
 invoke-direct {v1,v2}, Landroid/content/IntentFilter;-><init>(Ljava/lang/String;)V
 const-string v2,"android.net.wifi.WIFI_AP_STATE_CHANGED"
 invoke-virtual {v1,v2}, Landroid/content/IntentFilter;->addAction(Ljava/lang/String;)V
 invoke-virtual {p0,v0,v1}, Landroid/content/Context;->registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)Landroid/content/Intent;
 move-result-object v0
 return-void
.end method
