package nachos.vm;
import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);
		file = ThreadedKernel.fileSystem.open("swapFile", true);
		for(int i = 0; i < ipt.length; i++){
			ipt[i] = new PageFrame(null, new TranslationEntry(0, 0, false, false, false, false));
		}
	}

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		file.close();
		ThreadedKernel.fileSystem.remove("swapFile");
		super.terminate();
	}
	
	
	void syncFlush(boolean flush) {
		for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
			TranslationEntry entry = Machine.processor().readTLBEntry(i);
			if (entry.valid) {
				TranslationEntry translationEntry = ipt[entry.ppn].entry;
				if (translationEntry.valid && translationEntry.vpn == entry.vpn) {
					translationEntry.used |= entry.used;
					translationEntry.dirty |= entry.dirty;
				}
			}
			if (flush) {
				entry.valid = false;
				Machine.processor().writeTLBEntry(i, entry);
			}
		}
	}
	
	protected void handlePageFault(TranslationEntry te, int pid, int vpn, VMProcess process){
		int ppn;
		if(freePages.size() == 0){
			ppn = clockAlgorithm();
		}else{
			ppn = ((Integer)UserKernel.freePages.removeFirst()).intValue();
		}
		updatePhysicalMemory(ppn, pid, process);
		te.valid = true;
	}
	
	private void updatePhysicalMemory(int ppn, int pid, VMProcess process){
		int vpn = ipt[ppn].entry.vpn;
		int spn;
		if(swap.isInSwap(vpn, pid)){
			spn = swapPageTable.get(new SwapKey(vpn, pid));
			swap.swapIn(spn, ppn, pid, vpn);
		}else{
			CoeffSection section = process.coffMap.get(new Integer(vpn)).section;
			int spn = process.coffMap.get(new Integer(vpn)).spn;
			section.loadPage(spn, process.pinVirtualPage(vpn, false));
		}
		
	}
	
	private int clockAlgorithm() {
		memorylock.acquire();
		while (pinCount == Machine.processor().getNumPhysPages()) 
			pinCon.sleep();

		syncFlush(false);
		// we find a not used and not pinned page, because ppn all used, so no null element
		while (ipt[victim].entry.used && !ipt[victim].pin) {
			ipt[victim].entry.used = false;
			victim = (victim + 1) % ipt.length;
		}
		
		int toEvict = victim;
		victim = (victim + 1) % ipt.length;
		
		
		TranslationEntry entry = ipt[toEvict].entry;
		
		if(entry.dirty ){
			swap.swapOut(toEvict);
		}else{
			entry.ppn = -1;
		}
		
		entry.valid = false ;
		
		memoryLock.release();

		return toEvict;
	}
	
	
	
	
	/** An inner class to act as a key for the inverted page table. */
	private static class SwapKey {
		SwapKey(int vpn, int pid) {
			this.vpn = vpn;
			this.pid = pid;
		}
		@Override
		public int hashCode() {
			return pid * Machine.processor().getNumPhysPages() + vpn; //There should be no collisions with this hash
		}

		@Override
		public boolean equals(Object x) {
			if (this == x)
				return true;
			else if (x instanceof SwapKey) {
				SwapKey xKey = (SwapKey)x;
				return vpn.equals(xKey.vpn) && pid.equals(xKey.pid);
			} else {
				return false;
			}
		}
		private Integer vpn, pid;
	}
	
	private void updateIPT(int ppn, TranslationEntry entry){
		ipt[ppn].entry = entry;
	}
	
	protected class Swap{
		public Swap() {
		}
		private void swapIn(int spn, int ppn, int pid, int vpn) {
			swapLock.acquire();
			byte[] memory = Machine.processor().getMemory();
			ipt[ppn].pinNum++;
			pinCount++;
			file.read(spn * Processor.pageSize, memory, ppn
					* Processor.pageSize, Processor.pageSize);
			swapPageTable.remove(new SwapKey(vpn, pid));
			freeSwapPages.add(new Integer(spn));
			swapLock.release();
		}
		
		private boolean isInSwap(int vpn, int pid){
			return swapPageTable.containsKey(new SwapKey(vpn, pid));
		}
		
		private int swapOut(int ppn) {
			swapLock.acquire();
			int spn;
			int size = freeSwapPages.size();
			if (size == 0){
				maxSpn++;
				freeSwapPages.add(new Integer(maxSpn));
			}
			spn = freeSwapPages.removeFirst().intValue();
			ipt[ppn].entry.valid = false;
			ipt[ppn].pinNum++;
			pinCount++;
			byte[] memory = Machine.processor().getMemory();
			file.write(spn * Processor.pageSize, memory, ppn
					* Processor.pageSize, Processor.pageSize);
			
			swapLock.release();
			return spn;
		}
	
		public void close() {
			file.close();
			// TODO: uncomment ThreadedKernel.fileSystem.remove("swapperinos");
		}

		Lock swapLock = new Lock();
	}
	
	
	private class PageFrame{
		PageFrame(Process process, TranslationEntry e){
			this.process = process;
			this.entry = e;
		}
		Process process = null;
		TranslationEntry entry;
		int pinNum = 0;
	}
	
	
	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;
	protected static OpenFile file;
	private static final char dbgVM = 'v';
	protected int pinCount = 0;
	private Lock memorylock = new Lock();
	private Condition pinCon = new Condition(memorylock);
	//inverted page table ppn -> pageframe
	private PageFrame[] ipt = new PageFrame[Machine.processor().getNumPhysPages()];
	
	private HashMap<SwapKey, Integer> swapPageTable = new HashMap<SwapKey, Integer>();
	
	private Swap swap = new Swap();
	
	private int victim  = 0;
	
	private int maxSpn = 0;
	
	private static LinkedList<Integer> freeSwapPages = new LinkedList<Integer>();
}
