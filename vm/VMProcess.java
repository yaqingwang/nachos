package nachos.vm;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;

import userprog.CoffSection;
import userprog.UserKernel;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 *  A <tt>UserProcess</tt> that supports demand-paging.
 */

public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
		if (kernel == null) {
			try {
				kernel = (VMKernel) ThreadedKernel.kernel;
			} catch (ClassCastException cce) {
				System.out.println("Not VMKernel");
			}
		}
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		super.saveState();
		kernel.syncFlush(true);
	}
	private void syncHelper(TranslationEntry entry){
		for(int i = 0; i < pageTable.length; i++){
			if(pageTable[i].ppn == entry.ppn){
				pageTable[i].dirty |= entry.dirty;
				pageTable[i].used |= entry.used;
			}
		}
	}
	private void syncPageTable(){
		for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
			TranslationEntry entry = Machine.processor().readTLBEntry(i);
			syncHelper(entry);
		}
	}
	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		//super.restoreState();
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		
		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i++)
			pageTable[i] = new TranslationEntry(i, -1, false, false, false,
					false);
		UserKernel.memoryLock.acquire();

		if (UserKernel.freePages.size() < numPages) {
		    UserKernel.memoryLock.release();
		    coff.close();
		    Lib.debug(dbgProcess, "\tinsufficient physical memory");
		    return false;
		}
		
		// load sections
		for (int s=0; s<coff.getNumSections(); s++) {
		    CoffSection section = coff.getSection(s);
		    
		    Lib.debug(dbgProcess, "\tinitializing " + section.getName()
			      + " section (" + section.getLength() + " pages)");

		    for (int i=0; i<section.getLength(); i++) {
				int vpn = section.getFirstVPN()+i;
				pageTable[vpn].readOnly = section.isReadOnly();
				
				coffMap.put(new Integer(vpn), new CoffPage(section, i));
		    }
		}
		return true;
	}
	protected void unloadSections() {
		for (int vpn = 0; vpn < pageTable.length; vpn++) {
			if (pageTable[vpn].ppn >= 0){
				kernel.freePages.add(new Integer(pageTable[vpn].ppn));
			}
		}
		
		for (int vpn = 0; vpn < pageTable.length; vpn++) {
			if(kernel.swap.isInSwap(vpn, ProcessID)){
				swap.clear(vpn, ProcessID);
			}
		}
		
	}
	
	
	
	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		super.unloadSections();
	}
	
	
	
	
	private void handleTLBMiss(int vaddr){
		int vpn = Processor.pageFromAddress(vaddr);
		TranslationEntry replaceEntry = pageTable[vpn];
		int TLBSize = Machine.processor().getTLBSize();
		//int replace = -1;
		boolean write = false;
		
		if(!replaceEntry.valid){
			kernel.handlePageFault(replaceEntry, processID, vpn);
		}
		//update TLB
		for (int i = 0; i < TLBSize && !write; i++) {
			TranslationEntry entry = Machine.processor().readTLBEntry(i);
			if(!entry.valid){
				write = true;
				//replace = i;
				Machine.processor().writeTLBEntry(i, replaceEntry);
			}
		}
		// if not write, random select a page to write in
		if(!write){
			int randomIndex = new Random().nextInt(TLBSize);
			TranslationEntry oldEntry = Machine.processor().readTLBEntry(randomIndex);
			//TO DO
			if (oldEntry.dirty || oldEntry.used){}
				kernel.syncFlush(false);
			Machine.processor().writeTLBEntry(randomIndex,replaceEntry);
		}
		

		
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionTLBMiss:
			handleTLBMiss(processor.readRegister(processor.regBadVAddr));
			break;
		default:
			super.handleException(cause);
			break;
		}
	}
	class CoffPage {
		public CoffPage(CoffSection section, int spn) {
			this.section = section;
			this.spn = spn;
		}

		public CoffSection section;
		public int spn;
	}
	
	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
	
	protected HashMap<Integer, CoffPage> coffMap = new HashMap<Integer, CoffPage>();
	
	private VMKernel kernel;
}
