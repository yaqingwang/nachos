package nachos.threads;

import java.util.*;
import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
		waitUntilQueue = new LinkedList<waitUntilThread>();
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		boolean intStatus = Machine.interrupt().disable();
		long time = Machine.timer().getTime();
		if(!waitUntilQueue.isEmpty()){
			ListIterator<waitUntilThread> it = waitUntilQueue.listIterator();
			while(it.hasNext()){
				waitUntilThread thread = it.next();
				if(time >= thread.getWakeTime()){
					it.remove();
					thread.getThread().ready();
				}
			}
		}
		Machine.interrupt().restore(intStatus);
		KThread.currentThread().yield();
		
	}
    
	
	
	
	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		// for now, cheat just to get something working (busy waiting is bad)
		boolean intStatus = Machine.interrupt().disable();
		long wakeTime = Machine.timer().getTime() + x;
		waitUntilThread thread = new waitUntilThread(KThread.currentThread(), wakeTime);
		waitUntilQueue.add(thread);
		KThread.sleep();
		Machine.interrupt().restore(intStatus);
		
	}
	
	// Place this function inside Alarm. And make sure Alarm.selfTest() is called inside ThreadedKernel.selfTest() method.

	public static void selfTest() {
	    KThread t1 = new KThread(new Runnable() {
	        public void run() {
	            long time1 = Machine.timer().getTime();
	            int waitTime = 10000;
	            System.out.println("Thread calling wait at time:" + time1);
	            ThreadedKernel.alarm.waitUntil(waitTime);
	            System.out.println("Thread woken up after:" + (Machine.timer().getTime() - time1));
	            Lib.assertTrue((Machine.timer().getTime() - time1) > waitTime, " thread woke up too early.");
	            
	        }
	    });
	    t1.setName("T1");
	    t1.fork();
	    t1.join();
	}
	
	private LinkedList<waitUntilThread> waitUntilQueue = null;
	private waitUntilThread waitUntil = null;
}

class waitUntilThread{
	private KThread thread;
	private long wakeTime;
	public waitUntilThread(KThread thread, long wakeTime)
	{
		this.thread = thread;
		this.wakeTime = wakeTime;
	}
	public long getWakeTime()
	{
		return wakeTime;
	}
	public KThread getThread()
	{
		return thread;
	}
} 
