/*
 * FindBugs - Find bugs in Java programs
 * Copyright (C) 2004-2006 University of Maryland
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package edu.umd.cs.findbugs.detect;

import java.util.HashSet;

import org.apache.bcel.classfile.Code;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;

/**
 * We found a problem with the new OpenJDK that everyone is now using to compile
 * and run java code. In particular, the java.util.logging.Logger behavior has
 * changed. Instead of using strong references, it now uses weak references
 * internally. That's a reasonable change, but unfortunately some code relies on
 * the old behavior - when changing logger configuration, it simply drops the
 * logger reference. That means that the garbage collector is free to reclaim
 * that memory, which means that the logger configuration is lost.
 */
public class LostLoggerDueToWeakReference extends BytecodeScanningDetector {

	BugReporter bugReporter;

	HashSet<String> namesOfSetterMethods = new HashSet<String>();

	public LostLoggerDueToWeakReference(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
		namesOfSetterMethods.add("addHandler");
		namesOfSetterMethods.add("setUseParentHandlers");
		namesOfSetterMethods.add("setLevel");
		namesOfSetterMethods.add("setFilter");
	}

	@Override
	public void visit(Code code) {
		if (getMethodSig().indexOf("Logger") == -1) {
			sawGetLogger = sawSetLogger = -1;
			loggerEscaped = loggerImported = false;
			super.visit(code); // make callbacks to sawOpcode for all opcodes
			if (false) {
				System.out.println(getFullyQualifiedMethodName());
				System.out.printf("%d %d %s %s\n", sawGetLogger, sawSetLogger, loggerEscaped, loggerImported);

			}
			if (sawGetLogger >= 0 && sawSetLogger >= 0 && !loggerEscaped && !loggerImported)
				bugReporter.reportBug(new BugInstance(this, "LG_LOST_LOGGER_DUE_TO_WEAK_REFERENCE", NORMAL_PRIORITY).addClassAndMethod(this).addSourceLine(
				        this, sawSetLogger));
		}
	}

	int sawGetLogger;

	int sawSetLogger;

	boolean loggerEscaped;

	boolean loggerImported;

	@Override
	public void sawOpcode(int seen) {
		switch (seen) {
		case INVOKESTATIC:
			if (getClassConstantOperand().equals("java/util/logging/Logger") && getNameConstantOperand().equals("getLogger")) {
				sawGetLogger = getPC();
				break;
			}
			checkForImport();
			break;
		case INVOKEVIRTUAL:
			if (getClassConstantOperand().equals("java/util/logging/Logger")
			        && namesOfSetterMethods.contains(getNameConstantOperand())) {
				sawSetLogger = getPC();
				break;
			}
			checkForImport();
			checkForMethodExportImport();
			break;

		case INVOKEINTERFACE:
		case INVOKESPECIAL:
			checkForImport();
			checkForMethodExportImport();
			break;

		case GETFIELD:
		case GETSTATIC:
			checkForImport();
			break;
		case PUTFIELD:
		case PUTSTATIC:
			checkForFieldEscape();
		}

	}

	private void checkForImport() {
		if (getSigConstantOperand().endsWith("Logger;"))
			loggerImported = true;
	}

	private void checkForMethodExportImport() {
		String sig = getSigConstantOperand();
		int pos = sig.indexOf(")");
		int loggerPos = sig.indexOf("Logger");
		if (0 <= loggerPos && loggerPos < pos)
			loggerEscaped = true;
	}

	private void checkForFieldEscape() {
		String sig = getSigConstantOperand();
		if (sig.indexOf("Logger") >= 0)
			loggerEscaped = true;
	}

	private void emitWarning() {
		System.out.println("Warn about " + getMethodName()); // TODO
	}

}