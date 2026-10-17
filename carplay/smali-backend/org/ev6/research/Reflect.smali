.class public Lorg/ev6/research/Reflect;
.super Ljava/lang/Object;
.method public static call(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;
 .locals 8
 invoke-virtual {p0}, Ljava/lang/Object;->getClass()Ljava/lang/Class;
 move-result-object v0
 :classes
 if-eqz v0,:missing
 invoke-virtual {v0}, Ljava/lang/Class;->getDeclaredMethods()[Ljava/lang/reflect/Method;
 move-result-object v1
 const/4 v2,0
 array-length v3,v1
 :methods
 if-ge v2,v3,:super
 aget-object v4,v1,v2
 invoke-virtual {v4},Ljava/lang/reflect/Method;->getName()Ljava/lang/String;
 move-result-object v5
 invoke-virtual {v5,p1},Ljava/lang/String;->equals(Ljava/lang/Object;)Z
 move-result v5
 if-eqz v5,:next
 invoke-virtual {v4},Ljava/lang/reflect/Method;->getParameterTypes()[Ljava/lang/Class;
 move-result-object v5
 array-length v5,v5
 array-length v6,p2
 if-ne v5,v6,:next
 const/4 v5,1
 invoke-virtual {v4,v5},Ljava/lang/reflect/Method;->setAccessible(Z)V
 invoke-virtual {v4,p0,p2},Ljava/lang/reflect/Method;->invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;
 move-result-object v5
 return-object v5
 :next
 add-int/lit8 v2,v2,1
 goto :methods
 :super
 invoke-virtual {v0},Ljava/lang/Class;->getSuperclass()Ljava/lang/Class;
 move-result-object v0
 goto :classes
 :missing
 new-instance v0,Ljava/lang/NoSuchMethodException;
 invoke-direct {v0,p1},Ljava/lang/NoSuchMethodException;-><init>(Ljava/lang/String;)V
 throw v0
.end method
