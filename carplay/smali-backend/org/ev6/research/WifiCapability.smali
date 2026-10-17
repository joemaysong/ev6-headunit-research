.class public Lorg/ev6/research/WifiCapability;
.super Ljava/lang/Object;
.method public static enable(Ljava/lang/Object;)V
 .locals 4
 invoke-virtual {p0},Ljava/lang/Object;->getClass()Ljava/lang/Class;
 move-result-object v0
 invoke-virtual {v0},Ljava/lang/Class;->getClassLoader()Ljava/lang/ClassLoader;
 move-result-object v0
 const-string v1,"com.tcc.iap2server.Iap2InitInformationMsg"
 invoke-virtual {v0,v1},Ljava/lang/ClassLoader;->loadClass(Ljava/lang/String;)Ljava/lang/Class;
 move-result-object v0
 const-string v1,"iAP2UsedWifiInformation"
 invoke-virtual {v0,v1},Ljava/lang/Class;->getDeclaredField(Ljava/lang/String;)Ljava/lang/reflect/Field;
 move-result-object v0
 const/4 v1,1
 invoke-virtual {v0,v1},Ljava/lang/reflect/Field;->setAccessible(Z)V
 const/4 v2,0
 invoke-virtual {v0,v2},Ljava/lang/reflect/Field;->getInt(Ljava/lang/Object;)I
 move-result v3
 invoke-static {v3},Ljava/lang/Integer;->toString(I)Ljava/lang/String;
 move-result-object v3
 const-string v1,"WIFI_INFORMATION_CAPABILITY_BEFORE="
 invoke-virtual {v1,v3},Ljava/lang/String;->concat(Ljava/lang/String;)Ljava/lang/String;
 move-result-object v1
 invoke-static {v1},Lorg/ev6/research/ProbeService;->mark(Ljava/lang/String;)V
 const/4 v1,1
 invoke-virtual {v0,v2,v1},Ljava/lang/reflect/Field;->setInt(Ljava/lang/Object;I)V
 const-string v0,"WIFI_INFORMATION_CAPABILITY_ENABLED_IN_DIAGNOSTIC_PROCESS"
 invoke-static {v0},Lorg/ev6/research/ProbeService;->mark(Ljava/lang/String;)V
 return-void
.end method
