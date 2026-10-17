.class public Lorg/ev6/research/ControlFiles;
.super Ljava/lang/Object;
.method public static write(Landroid/content/Context;Ljava/lang/String;Z)V
 .locals 3
 const/4 v0,0
 invoke-virtual {p0,p1,v0},Landroid/content/Context;->openFileOutput(Ljava/lang/String;I)Ljava/io/FileOutputStream;
 move-result-object v0
 const/16 v1,48
 if-eqz p2,:write
 const/16 v1,49
 :write
 invoke-virtual {v0,v1},Ljava/io/FileOutputStream;->write(I)V
 invoke-virtual {v0},Ljava/io/FileOutputStream;->close()V
 return-void
.end method
.method public static enabled(Landroid/content/Context;)Z
 .locals 2
 :start
 const-string v0,"automation.enabled"
 invoke-virtual {p0,v0},Landroid/content/Context;->openFileInput(Ljava/lang/String;)Ljava/io/FileInputStream;
 move-result-object v0
 invoke-virtual {v0},Ljava/io/FileInputStream;->read()I
 move-result v1
 invoke-virtual {v0},Ljava/io/FileInputStream;->close()V
 const/16 v0,49
 if-ne v1,v0,:off
 const/4 v0,1
 :end
 return v0
 .catch Ljava/io/FileNotFoundException; {:start .. :end} :missing
 :off
 const/4 v0,0
 return v0
 :missing
 move-exception v0
 const/4 v0,1
 return v0
.end method
.method public static init(Landroid/content/Context;)V
 .locals 3
 invoke-virtual {p0},Landroid/content/Context;->getFilesDir()Ljava/io/File;
 move-result-object v0
 new-instance v1,Ljava/io/File;
 const-string v2,"automation.enabled"
 invoke-direct {v1,v0,v2},Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V
 invoke-virtual {v1},Ljava/io/File;->exists()Z
 move-result v0
 if-nez v0,:session
 const/4 v0,1
 invoke-static {p0,v2,v0},Lorg/ev6/research/ControlFiles;->write(Landroid/content/Context;Ljava/lang/String;Z)V
 :session
 const/4 v0,0
 const-string v1,"backend.ready"
 invoke-static {p0,v1,v0},Lorg/ev6/research/ControlFiles;->write(Landroid/content/Context;Ljava/lang/String;Z)V
 const-string v1,"session.state"
 invoke-static {p0,v1,v0},Lorg/ev6/research/ControlFiles;->write(Landroid/content/Context;Ljava/lang/String;Z)V
 return-void
.end method
.method public static session(Landroid/content/Context;Z)V
 .locals 1
 const-string v0,"session.state"
 invoke-static {p0,v0,p1},Lorg/ev6/research/ControlFiles;->write(Landroid/content/Context;Ljava/lang/String;Z)V
 if-eqz p1,:off
 const-string v0,"GENUINE_WIRELESS_SESSION_CONNECTED"
 goto :mark
 :off
 const-string v0,"GENUINE_WIRELESS_SESSION_DISCONNECTED"
 :mark
 invoke-static {v0},Lorg/ev6/research/ProbeService;->mark(Ljava/lang/String;)V
 return-void
.end method
