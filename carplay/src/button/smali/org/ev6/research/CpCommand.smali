.class public Lorg/ev6/research/CpCommand;
.super Landroid/app/Service;
.method public constructor <init>()V
 .locals 0
 invoke-direct {p0},Landroid/app/Service;-><init>()V
 return-void
.end method
.method public onBind(Landroid/content/Intent;)Landroid/os/IBinder;
 .locals 1
 const/4 v0,0
 return-object v0
.end method
.method public onStartCommand(Landroid/content/Intent;II)I
 .locals 8
 :try_start
 if-eqz p1,:done
 const-string v0,"android.bluetooth.device.extra.DEVICE"
 invoke-virtual {p1,v0},Landroid/content/Intent;->getParcelableExtra(Ljava/lang/String;)Landroid/os/Parcelable;
 move-result-object v0
 instance-of v1,v0,Landroid/bluetooth/BluetoothDevice;
 if-eqz v1,:done
 check-cast v0,Landroid/bluetooth/BluetoothDevice;
 invoke-virtual {v0},Landroid/bluetooth/BluetoothDevice;->getBondState()I
 move-result v1
 const/16 v2,12
 if-ne v1,v2,:done
 invoke-virtual {v0},Landroid/bluetooth/BluetoothDevice;->getAddress()Ljava/lang/String;
 move-result-object v0
 const-string v1,"phoneprojection"
 invoke-virtual {p0,v1},Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;
 move-result-object v1
 check-cast v1,Landroid/phoneprojection/PPManager;
 invoke-virtual {v1},Landroid/phoneprojection/PPManager;->getCPConnectedBTAddress()Ljava/lang/String;
 move-result-object v1
 invoke-virtual {v0,v1},Ljava/lang/String;->equals(Ljava/lang/Object;)Z
 move-result v1
 const-string v2,"connect"
 if-eqz v1,:action_ready
 const-string v2,"disconnect"
 :action_ready
 # Root-armed diagnostic mode records only selected target, never a command.
 invoke-virtual {p0},Landroid/content/Context;->getFilesDir()Ljava/io/File;
 move-result-object v3
 new-instance v1,Ljava/io/File;
 const-string v4,"diagnostic-only"
 invoke-direct {v1,v3,v4},Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V
 invoke-virtual {v1},Ljava/io/File;->exists()Z
 move-result v1
 if-eqz v1,:normal_command
 new-instance v1,Ljava/io/File;
 const-string v4,"selection-probe"
 invoke-direct {v1,v3,v4},Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V
 new-instance v3,Ljava/io/FileOutputStream;
 invoke-direct {v3,v1},Ljava/io/FileOutputStream;-><init>(Ljava/io/File;)V
 const-string v4,"US-ASCII"
 invoke-virtual {v0,v4},Ljava/lang/String;->getBytes(Ljava/lang/String;)[B
 move-result-object v0
 invoke-virtual {v3,v0},Ljava/io/FileOutputStream;->write([B)V
 invoke-virtual {v3},Ljava/io/FileOutputStream;->close()V
 goto :done
 :normal_command
 # Root controller publishes readiness. Drop unavailable clicks silently.
 new-instance v1,Ljava/io/File;
 invoke-virtual {p0},Landroid/content/Context;->getFilesDir()Ljava/io/File;
 move-result-object v3
 const-string v4,"controller-ready"
 invoke-direct {v1,v3,v4},Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V
 invoke-virtual {v1},Ljava/io/File;->exists()Z
 move-result v1
 if-eqz v1,:done
 # Closed command schema, private AtomicFile, no addresses in logs.
 new-instance v1,Ljava/io/File;
 const-string v4,"command"
 invoke-direct {v1,v3,v4},Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V
 new-instance v3,Landroid/util/AtomicFile;
 invoke-direct {v3,v1},Landroid/util/AtomicFile;-><init>(Ljava/io/File;)V
 invoke-virtual {v3},Landroid/util/AtomicFile;->startWrite()Ljava/io/FileOutputStream;
 move-result-object v4
 new-instance v5,Ljava/lang/StringBuilder;
 invoke-direct {v5},Ljava/lang/StringBuilder;-><init>()V
 invoke-static {},Ljava/lang/System;->nanoTime()J
 move-result-wide v6
 invoke-virtual {v5,v6,v7},Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;
 const-string v6," "
 invoke-virtual {v5,v6},Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
 invoke-virtual {v5,v2},Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
 invoke-virtual {v5,v6},Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
 invoke-virtual {v5,v0},Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
 invoke-virtual {v5},Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
 move-result-object v5
 const-string v6,"US-ASCII"
 invoke-virtual {v5,v6},Ljava/lang/String;->getBytes(Ljava/lang/String;)[B
 move-result-object v5
 invoke-virtual {v4,v5},Ljava/io/FileOutputStream;->write([B)V
 invoke-virtual {v3,v4},Landroid/util/AtomicFile;->finishWrite(Ljava/io/FileOutputStream;)V
 const-string v0,"EV6CarPlayButton"
 const-string v1,"USER_COMMAND_ACCEPTED"
 invoke-static {v0,v1},Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
 goto :done
 :try_end
 .catch Ljava/lang/Exception; {:try_start .. :try_end} :error
 :done
 const/4 v0,2
 return v0
 :error
 move-exception v0
 const-string v0,"EV6CarPlayButton"
 const-string v1,"COMMAND_FAILED"
 invoke-static {v0,v1},Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I
 goto :done
.end method
