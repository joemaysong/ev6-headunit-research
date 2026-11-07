/** One exact native stop invocation off Binder/main threads. ACK is not completion. */
public final class ProjectionAaStopWorker {
 /** Implementations must pin the exact request/transport while validating and stopping. */
 public interface Target {boolean stopIfCurrent()throws Exception;}
 public interface Executor {void execute(Runnable work)throws Exception;}
 private final Target target;private final Executor executor;
 private boolean submitted,started,returned,failed;
 public ProjectionAaStopWorker(Target target,Executor executor){if(target==null||executor==null)throw new IllegalArgumentException();this.target=target;this.executor=executor;}
 public boolean request(){
  synchronized(this){if(submitted)return !failed;submitted=true;}
  final Thread caller=Thread.currentThread();
  try{executor.execute(new Runnable(){public void run(){
   synchronized(ProjectionAaStopWorker.this){if(started)return;started=true;if(Thread.currentThread()==caller){failed=true;return;}}
   try{if(!target.stopIfCurrent())throw new IllegalStateException("retired stop target");synchronized(ProjectionAaStopWorker.this){returned=true;}}
   catch(Exception unavailable){synchronized(ProjectionAaStopWorker.this){failed=true;}}
  }});synchronized(this){return !failed;}}
  catch(Exception uncertain){synchronized(this){failed=true;}return false;}
 }
 public synchronized boolean returned(){return returned;}
 public synchronized boolean failed(){return failed;}
}
