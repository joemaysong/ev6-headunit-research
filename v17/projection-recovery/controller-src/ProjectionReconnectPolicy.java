/** Recovery eligibility is never a START grant. Restart restores a deny tombstone, never a retry budget. */
public final class ProjectionReconnectPolicy {
 public static final int MAX_ATTEMPTS=3;
 public static final long WINDOW_MS=120000L,STABLE_MS=30000L;
 private String session="",sessionPeer="",peer="",cancelledSession="";private int sessionType,type,attempts;
 private long stableAt,expires,next;private boolean pending,inFlight;
 public String peer(){return peer;}public int type(){return type;}public int attempts(){return attempts;}
 public boolean observing(){return session.length()>0;}public int sessionType(){return sessionType;}public String sessionPeer(){return sessionPeer;}
 public String checkpoint(){String p=observing()?sessionPeer:peer;int t=observing()?sessionType:type;return p.length()==0?"":("EV6RECONNECT1|"+p+"|"+t+"|"+(observing()?session:cancelledSession)+"|END\n");}
 public void restore(String value){if(value.length()==0)return;String[] p=value.split("\\|",-1);if(p.length!=5||!p[0].equals("EV6RECONNECT1")||!p[1].matches("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}")||!p[2].matches("[12]")||!(p[3].length()==0||p[3].matches("[0-9a-f]{32}"))||!p[4].equals("END\n"))throw new IllegalArgumentException("reconnect-checkpoint");peer=p[1];type=Integer.parseInt(p[2]);cancelledSession=p[3];pending=inFlight=false;expires=next=0;session="";}
 public void connected(String id,String target,int protocol,long now){
  if(id==null||target==null||!target.matches("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}")||(protocol!=1&&protocol!=2))throw new IllegalArgumentException();
  if(id.equals(cancelledSession))return;
  if(!session.equals(id)){if(expires==0&&!inFlight){peer="";attempts=0;}session=id;sessionPeer=target;sessionType=protocol;stableAt=now;}
  pending=false;inFlight=false;
  if(peer.length()>0&&(!peer.equals(target)||type!=protocol||now-stableAt>=STABLE_MS)){peer="";attempts=0;expires=next=0;}
 }
 public void ended(String id,long now){
  if(!observing()||!session.equals(id))return;String old=sessionPeer;int protocol=sessionType;cancelledSession=session;session="";
  if(peer.length()==0){peer=old;type=protocol;attempts=0;expires=now+WINDOW_MS;}
  if(!peer.equals(old)||type!=protocol)return;
  inFlight=false;pending=now<expires&&attempts<MAX_ATTEMPTS;next=now+delay();
 }
 private long delay(){return attempts==0?3000L:attempts==1?10000L:20000L;}
 /** A button/maintenance cancels both pending and future recovery from the observed session. */
 public void cancel(){if(observing()){peer=sessionPeer;type=sessionType;cancelledSession=session;}session="";pending=inFlight=false;expires=next=0;}
 public boolean window(long now){return peer.length()>0&&expires>now&&attempts<MAX_ATTEMPTS;}
 public boolean matches(String target,int protocol){return peer.equals(target)&&type==protocol;}
 public boolean blocksAutomatic(String target,int protocol,long now){return matches(target,protocol)||window(now);}
 public long claimedRemaining(String target,int protocol,long now){return inFlight&&matches(target,protocol)&&expires>now?expires-now:0;}
 public boolean due(long now){return pending&&window(now)&&now>=next;}
 public boolean claim(String target,int protocol,long now){if(!matches(target,protocol)||!due(now))return false;pending=false;inFlight=true;attempts++;return true;}
 public void failed(long now){if(!inFlight)return;inFlight=false;pending=window(now);next=now+delay();}
}
