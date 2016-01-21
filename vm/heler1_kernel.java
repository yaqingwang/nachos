protected TranslationEntry handlePageFault(int vpn) {
		VMKernel.Swapper swapper = VMKernel.swapper;
		VMKernel.IPT ipt = swapper.getIPT();
		int ppn = ipt.getPPN();
		int out = -1;
		if (ipt.getPage(ppn) != null)
			out = ipt.getVPN(ppn);
boolean debugSwap = true;
		// Evict PTE if memory is full
		VMProcess process = null;
		if (out >= 0) {
			process = ipt.getPage(ppn).process;
			if (process == null)
				process = (VMProcess) this;
			process.pteLock.acquire();
			process.pageTable[out].valid = false;
			PTE2TLB(process.pageTable[out]);

			// Write to swap file if page is dirty
			if (process.pageTable[out].dirty) {
				pinVirtualPage(vpn, false);
				process.spns[out] = swapper.writeSwap(process.spns[out], ppn); 
				if (debugSwap) System.out.println(process.processID + " writing " + process.spns[out] + " " + Arrays.toString(process.spns));
				unpinVirtualPage(vpn);
				process.pageTable[out].valid = false;
			}
			process.pteLock.release();
		}

		// Check if page is in swap file, otherwise load page
		pteLock.acquire();
		if (swapper.inSwapFile(pageTable[vpn], spns)) {
			pinVirtualPage(vpn, true);
			swapper.readSwap(spns[vpn], ppn); if (debugSwap) System.out.println(processID + " reading " + spns[vpn] + " " + Arrays.toString(spns));
			unpinVirtualPage(vpn);
			pageTable[vpn].valid = true;
			pageTable[vpn].ppn = ppn;
		} else {
			switch (map.vpns[vpn]) {
			case SectionMap.CODE:
				allocateCodePage(vpn, ppn);
				break;
			case SectionMap.DATA:
				allocateDataPage(vpn, ppn);
				break;
			case SectionMap.STACK:
				allocateStackPage(vpn, ppn);
				break;
			}
		}
		pteLock.release();

		// Sync
		ipt.update(ppn, (VMProcess) this, new TranslationEntry(
				pageTable[vpn]));
		PTE2TLB(pageTable[vpn]);
		return pageTable[vpn];
	}