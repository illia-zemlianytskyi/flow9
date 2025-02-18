package com.area9innovation.flow;

import java.util.*;
import java.io.Writer;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.InvalidPathException;
import java.awt.datatransfer.DataFlavor;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.CharsetDecoder;
import java.io.FileInputStream;
import java.io.File;
import java.nio.charset.CodingErrorAction;
import java.net.URLDecoder;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.lang.Runtime;
import java.io.OutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.*;
import java.util.Arrays;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.concurrent.ConcurrentHashMap;
import com.sun.management.OperatingSystemMXBean;

public class Native extends NativeHost {
	private static final int NTHREDS = 16;
	private static MessageDigest md5original = null;
	private static ExecutorService threadpool = Executors.newFixedThreadPool(NTHREDS);
	private static OperatingSystemMXBean osBean = java.lang.management.ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);

	public Native() {
		try {
			md5original = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			md5original = null;
		}
	}

	public final Object println(Object arg) {
		String s = "";
		if (arg instanceof String) {
			s = arg.toString();
		} else {
			s = FlowRuntime.toString(arg);
		}

		try {
			synchronized (System.out) {
				PrintStream out = new PrintStream(System.out, true, "UTF-8");
				out.println(s);
			}
		} catch(UnsupportedEncodingException e) {
		}
		return null;
	}

	public final Object hostCall(String name, Object[] args) {
		return null;
	}

	public final Object failWithError(String msg) {
		try {
			PrintStream out = new PrintStream(System.out, true, "UTF-8");
			out.println("Runtime failure: " + msg);
			Thread.dumpStack();
		} catch(UnsupportedEncodingException e) {
		}
		System.exit(255);
		return null;
	}

	public final Object hostAddCallback(String name, Func0<Object> cb) {
		return null;
	}

	public final Object setClipboard(String text) {
		StringSelection selection = new StringSelection(text);
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		clipboard.setContents(selection, selection);

		return null;
	}

	public final Object setCurrentDirectory(String path) {
		return null;
	}

	public final String getCurrentDirectory() {
		return Paths.get(".").toAbsolutePath().normalize().toString();
	}

	public final String getClipboard() {
		try {
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			String data = (String) clipboard.getData(DataFlavor.stringFlavor);
			return data;
		} catch (UnsupportedFlavorException e) {
			return "";
		} catch (IOException e) {
			return "";
		}
	}

	public final Object getClipboardToCB(Func1<Object, String> cb) {
		try {
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			String data = (String) clipboard.getData(DataFlavor.stringFlavor);
			cb.invoke(data);
			return null;
		} catch (UnsupportedFlavorException e) {
			return null;
		} catch (IOException e) {
			return null;
		}
	}

	public final String getClipboardFormat(String mimetype) {
		return "";
	}

	public final String getApplicationPath() {
		File currentJavaJarFile = new File(Native.class.getProtectionDomain().getCodeSource().getLocation().getPath());
		String currentJavaJarFilePath = currentJavaJarFile.getAbsolutePath();
		return currentJavaJarFilePath;
	}

	public final String toString(Object value) {
		return FlowRuntime.toString(value);
	}

	public final String toBinary(Object value) {
		Map<Integer, Integer> structIdxs = new HashMap<Integer, Integer>();
		List<Struct> structDefs = new ArrayList<Struct>();

		StringBuilder buf = new StringBuilder();
		writeBinaryValue(value, buf, structIdxs, structDefs);
		StringBuilder buf2 = new StringBuilder();
		writeBinaryInt32(buf.length() + 2, buf2);

		if (structDefs.size() == 0) {
			writeCharValue(0xFFF7, buf);
		} else {
			if (structDefs.size() > 65535) {
				writeCharValue(0xFFF9, buf);
				writeBinaryInt32(structDefs.size(), buf);
			} else {
				writeCharValue(0xFFF8, buf);
				writeCharValue(structDefs.size(), buf);
			}
		}
		for (Struct struct_def : structDefs) {
			writeCharValue(0xFFF8, buf); writeCharValue(0x0002, buf);
			writeCharValue(struct_def.getFields().length, buf);

			String s = struct_def.getTypeName();
			int str_len = s.length();
			writeCharValue(0xFFFA, buf);
			writeCharValue(str_len, buf);
			for (int i = 0; i < str_len; i++) {
				char v = s.charAt(i);
				writeCharValue(v, buf);
			}
		}
		return buf2.toString() + buf.toString();
	}

	final void writeCharValue(int c, StringBuilder buf) {
		buf.append(Character.toChars(c & 0xffff));
	}

	final void writeBinaryInt32(int i, StringBuilder buf) {
		short low = (short) (i & 0xffff);
		short high = (short) (i >> 16);
		writeCharValue(low, buf);
		writeCharValue(high, buf);
	}

	final void writeBinaryValue(Object value, StringBuilder buf, Map<Integer, Integer> structIdxs, List<Struct> structDefs) {
		if (value == null) {
			writeCharValue(0xffff, buf);
		} else if (value instanceof String) {
			String s = (String) value;
			int str_len = s.length();
			if (str_len > 65535) {
				writeCharValue(0xFFFB, buf);
				writeBinaryInt32(str_len, buf);
			} else {
				writeCharValue(0xFFFA, buf);
				writeCharValue(str_len, buf);
			}
			for (int i = 0; i < str_len; i++) {
				char v = s.charAt(i);
				writeCharValue(v, buf);
			}
		} else if (value instanceof Object[]) {
			Object[] arr = (Object[])value;
			int l = arr.length;
			if (l == 0) {
				writeCharValue(0xFFF7, buf);
			} else {
				if (l > 65535) {
					writeCharValue(0xFFF9, buf);
					writeBinaryInt32(l, buf);
				} else {
					writeCharValue(0xFFF8, buf);
					writeCharValue(l, buf);
				}
				for (int i = 0; i < arr.length; i++) {
					writeBinaryValue(arr[i], buf, structIdxs, structDefs);
				}
			}
		} else if (value instanceof Function) {
			System.out.println("Not implemented: toBinary of " + value);
		} else if (value instanceof Double) {
			writeCharValue(0xFFFC, buf);

			java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN);
			bb.putDouble((Double)value);
			for (int i = 0; i < 8; i += 2) {
				writeCharValue(bb.getShort(i), buf);
			}
		} else if (value instanceof Integer) {
			Integer int_value = (Integer) value;
			if ((int_value & 0xFFFF8000) != 0) {
				writeCharValue(0xFFF5, buf);
				writeBinaryInt32(int_value, buf);
			} else {
				writeCharValue(int_value, buf);
			}
		} else if (value instanceof Boolean) {
			Boolean b = (Boolean) value;
			writeCharValue(b ? 0xFFFE : 0xFFFD, buf);
		} else if (value instanceof Struct) {
			Struct s = (Struct) value;
			int struct_id = s.getTypeId();

			Object[] struct_fields = s.getFields();
			RuntimeType[] field_types = s.getFieldTypes();
			int fields_count = struct_fields.length;

			int struct_idx = 0;
			if (structIdxs.containsKey(struct_id)) {
				struct_idx = structIdxs.get(struct_id);
			} else {
				struct_idx = structDefs.size();
				structIdxs.put(struct_id, struct_idx);
				structDefs.add(s);
			}

			writeCharValue(0xFFF4, buf);
			writeCharValue(struct_idx, buf);

			for (int i=0; i < fields_count; ++i)  {
				writeBinaryValue(struct_fields[i], buf, structIdxs, structDefs);
			}
		} else {
			System.out.println("Not implemented: toBinary of " + value);
		}
	}

	public final double stringbytes2double(String s) {
		int l = java.lang.Math.min(s.length(), 4);
		java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(8);
		for (int i = 0; i < l; i++) {
			char v = s.charAt(i);
			byte b0 = (byte) (v & 0xff);
			byte b1 = (byte) (v >>> 8);
			bb.put(2*i, b0);
			bb.put(2*i + 1, b1);
		}
		return bb.order(java.nio.ByteOrder.LITTLE_ENDIAN).getDouble(0);
	}

	public final int stringbytes2int(String s) {
		int l = java.lang.Math.min(s.length(), 2);
		java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(4);
		for (int i = 0; i < l; i++) {
			char v = s.charAt(i);
			byte b0 = (byte) (v & 0xff);
			byte b1 = (byte) (v >> 8);
			bb.put(2*i, b0);
			bb.put(2*i + 1, b1);
		}
		return bb.order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt(0);
	}

	public final String fromBinary(String s, Object defvalue, Object fixups) {
		return s;
	}

	public final Object gc() {
		System.gc();
		return null;
	}

	public final Object addHttpHeader(String data) {
		return null;
	}

	public final String getCgiParameter(String name) {
		return "";
	}

	public final Object[] subrange(Object[] arr, int start, int len) {
		// Make sure we are within bounds
		if (start < 0 || len < 1 || start >= arr.length) return new Object[0];
		len = clipLenToRange(start, len, arr.length);
		return Arrays.copyOfRange(arr, start, start + len);
	}

	private final int clipLenToRange(int start, int len, int size) {
		int end = start + len;
		if (end > size || end  < 0) {
			len = size - start;
		}
		return len;
	}

	public final boolean isArray(Object obj) {
		return FlowRuntime.isArray(obj);
	}

	public final boolean isSameStructType(Object a, Object b) {
		return a != null && b != null &&
			   a instanceof Struct && b instanceof Struct &&
			   ((Struct)a).getTypeId() == ((Struct)b).getTypeId();
	}

	public final boolean isSameObj(Object a, Object b) {
		if (a == b)
			return true;
		if (a instanceof Number || a instanceof String)
			return b != null && a.getClass() == b.getClass() && a.equals(b);
		return false;
	}

	public final int length(Object[] arr) {
		return arr.length;
	}

	public final int strlen(String str) {
		return str.length();
	}

	public final int strIndexOf(String str, String substr) {
		return str.indexOf(substr);
	}

	public final String strReplace(String s, String old, String _new) {
		return s.replace(old, _new);
	}

	public final int strRangeIndexOf(String str, String substr, Integer start, Integer end) {
		if (str == "" || start < 0)
			return -1;
		end = (end > str.length() || end < 0) ? str.length() : end;

		if (end >= str.length())
			return str.indexOf(substr, start);

		end -= substr.length() - 1;
		// TODO: possibly way to speedup this is to eliminate cycle
		for (int i = start; i < end; i++)
			if (str.startsWith(substr, i))
				return i;
		return -1;
	}

	public final String substring(String str, int start, int len) {
		int strlen = str.length();
		if (len < 0) {
			if (start < 0) len = 0;
			else {
				int smartLen1 = len + start;
				if (smartLen1 >= 0) len = 0;
				else {
					int smartLen2 = smartLen1 + strlen;
					if (smartLen2 <= 0) len = 0;
					else len = smartLen2;
				}
			}
		}
		if (start < 0) {
			int smartStart = start + strlen;
			if (smartStart > 0) start = smartStart;
			else start = 0;
		} else if (start >= strlen) {
			len = 0;
		}

		if (len < 1) return "";

		len = clipLenToRange(start, len, strlen);

		return str.substring(start, start + len);
	}

	public final String toLowerCase(String str) {
		return str.toLowerCase();
	}

	public final String toUpperCase(String str) {
		return str.toUpperCase();
	}

	public final Object[] string2utf8(String str) {
		ArrayList<Integer> bytesList = new ArrayList<Integer>();
		// We know we need at least this
		bytesList.ensureCapacity(str.length());

		for(int i = 0; i < str.length(); i++) {
			int x = str.codePointAt(i);

			if (x <= 0x7F) {
			bytesList.add(x);
			} else if (x <= 0x7FF) {
			int b2 = x & 0x3F;
			int b1 = (x >> 6) & 0x3F;

			bytesList.add(0xC0 | b1);
			bytesList.add(0x80 | b2);
			} else if (x <= 0xFFFF) {
			int b3 = x & 0x3F;
			int b2 = (x >> 6) & 0x3F;
			int b1 = (x >> 12) & 0x3F;

			bytesList.add(0xE0 | b1);
			bytesList.add(0x80 | b2);
			bytesList.add(0x80 | b3);
			} else if (x <= 0x1FFFFF) {
			int b4 = x & 0x3F;
			int b3 = (x >> 6) & 0x3F;
			int b2 = (x >> 12) & 0x3F;
			int b1 = (x >> 18) & 0x3F;

			bytesList.add(0xF0 | b1);
			bytesList.add(0x80 | b2);
			bytesList.add(0x80 | b3);
			bytesList.add(0x80 | b4);
			// Surrogate pair
			++i;
			} else if (x <= 0x3FFFFFF) {
			int b5 = x & 0x3F;
			int b4 = (x >> 6) & 0x3F;
			int b3 = (x >> 12) & 0x3F;
			int b2 = (x >> 18) & 0x3F;
			int b1 = (x >> 24) & 0x3F;

			bytesList.add(0xF8 | b1);
			bytesList.add(0x80 | b2);
			bytesList.add(0x80 | b3);
			bytesList.add(0x80 | b4);
			bytesList.add(0x80 | b5);
			// Surrogate pair
			++i;
			} else {
			}
		}
		return bytesList.toArray();
	}

	private final String utf82string(byte[] bytes) {
		StringBuilder str = new StringBuilder();
		Integer len = bytes.length;

		for(int i = 0; i<len; i++) {
			byte b1 = bytes[i];

			if ((b1 & 0xFC) == 0xF8 && i < len - 4) {
			byte b2 = bytes[i+1];
			byte b3 = bytes[i+2];
			byte b4 = bytes[i+3];
			byte b5 = bytes[i+4];
			i = i+4;

			int h1 = (b1 & 0x3) << 24;
			int h2 = (b2 & 0x3F) << 18;
			int h3 = (b3 & 0x3F) << 12;
			int h4 = (b4 & 0x3F) << 6;
			int h5 = 0x3F & b5;

			int h = h1 | h2 | h3 | h4 | h5;

			char[] cs = Character.toChars(h);

			// Surrogate pair
			str.append(cs[0]);
			str.append(cs[1]);
			} else if ((b1 & 0xF8) == 0xF0 && i < len - 3) {
			byte b2 = bytes[i+1];
			byte b3 = bytes[i+2];
			byte b4 = bytes[i+3];
			i = i+3;

			int h1 = (b1 & 0x7) << 18;

			int h2 = (b2 & 0x3F) << 12;
			int h3 = (b3 & 0x3F) << 6;
			int h4 = 0x3F & b4;

			int h = h1 | h2 | h3 | h4;

			char[] cs = Character.toChars(h);

			// Surrogate pair
			str.append(cs[0]);
			str.append(cs[1]);
			} else if ((b1 & 0xF0) == 0xE0 && i < len - 2) {
			byte b2 = bytes[i+1];
			byte b3 = bytes[i+2];
			i = i+2;

			int h1 = (b1 & 0xF) << 12;
			int h2 = (b2 & 0x3F) << 6;
			int h3 = 0x3F & b3;

			int h = h1 | h2 | h3;

			char[] cs = Character.toChars(h);

			str.append(cs[0]);
			} else if ((b1 & 0xE0) == 0xC0 && i < len - 1) {
			byte b2 = bytes[i+1];
			i = i+1;

			int h1 = (b1 & 0x1F) << 6;
			int h2 = 0x3F & b2;
			int h = h1 | h2;

			char[] cs = Character.toChars(h);

			str.append(cs[0]);
			} else {
			int h = b1 & 0xff;
			char[] cs = Character.toChars(h);

			str.append(cs[0]);
			}
		}

		return str.toString();
	}

	public final Object[] s2a(String str) {
		int l = str.length();
		Object[] rv = new Object[l];
		for (int i = 0; i < l; i++)
			rv[i] = Integer.valueOf(str.charAt(i)&0xFFFF);
		return rv;
	}

	public final String list2string(Struct list) {
		String rv = "";
		LinkedList<String> ll = new LinkedList<String>();
		int len = 0;
		for (Struct cur = list;;) {
			Object[] data = cur.getFields();
			if (data.length == 0) break;

			rv = ((String)data[0]);
			len += rv.length();
			ll.add(rv);
			cur = (Struct)data[1];
		}
		StringBuilder sb = new StringBuilder(len);
		// Load data from Cons'es to String builder in direct order
		for (Iterator i = ll.descendingIterator(); i.hasNext();) {
			String x = (String)i.next();
			sb.append(x);
		}
		return sb.toString();
	}

	public final Object headList(Struct list, Object _default) {
		Object[] data = list.getFields();
		if (data.length == 0) {
			return _default;
		} else {
			return data[0];
		}
	}

	public final Object tailList(Struct list, Object _default) {
		Object[] data = list.getFields();
		if (data.length == 0) {
			return _default;
		} else {
			return data[1];
		}
	}

	public final Object[] list2array(Struct list) {
		int count = 0;
		for (Struct cur = list;;) {
			Object[] data = cur.getFields();
			if (data.length == 0)
				break;
			count++;
			cur = (Struct)data[1];
		}
		Object[] rv = new Object[count];
		for (Struct cur = list;;) {
			Object[] data = cur.getFields();
			if (data.length == 0)
				break;
			rv[--count] = data[0];
			cur = (Struct)data[1];
		}
		return rv;
	}

	public final int bitXor(int a, int b) {
		return a^b;
	}

	public final int bitAnd(int a, int b) {
		return a&b;
	}

	public final int bitOr(int a, int b) {
		return a|b;
	}

	public final int bitNot(int a) {
		return ~a;
	}

	public final int bitShl(int a, int n) {
		return a << n;
	}

	public final int bitUshr(int a, int n) {
		return a >>> n;
	}

	public final Object[] concat(Object[] a, Object[] b) {
		Object[] rv = Arrays.copyOf(a, a.length + b.length);
		System.arraycopy(b, 0, rv, a.length, b.length);
		return rv;
	}

	public final Integer elemIndex(Object[] a, Object elem, Integer illegal) {
	if (elem == null) {
		for (Integer i = 0; i < a.length; i++)
			if (a[i] == null)
				return i;
		} else {
			for (Integer i = 0; i < a.length; i++)
				if (a[i] == elem || elem.equals(a[i]) || runtime.compareByValue(elem, a[i]) == 0)
				return i;
	}

	return illegal;
	}
	public final Object[] replace(Object[] a, int i, Object v) {
		if (a == null || i < 0)
			return new Object[0];
		Object[] rv = Arrays.copyOf(a, a.length > i ? a.length : i+1);
		rv[i] = v;
		return rv;
	}

	@SuppressWarnings("unchecked")
	public final <T1,T2> Object[] map(Object[] arr, Func1<T1,T2> clos) {
		Object[] rv = new Object[arr.length];
		for (int i = 0; i < arr.length; i++)
			rv[i] = clos.invoke((T2)arr[i]);
		return rv;
	}

	@SuppressWarnings("unchecked")
	public final <T> Object iter(Object[] arr, Func1<Object,T> clos) {
		for (int i = 0; i < arr.length; i++)
			clos.invoke((T)arr[i]);
		return null;
	}

	@SuppressWarnings("unchecked")
	public final <T1,T2> Object[] mapi(Object[] arr, Func2<T1,Integer,T2> clos) {
		Object[] rv = new Object[arr.length];
		for (int i = 0; i < arr.length; i++)
			rv[i] = clos.invoke(i, (T2)arr[i]);
		return rv;
	}

	@SuppressWarnings("unchecked")
	public final <T> Object iteri(Object[] arr, Func2<Object,Integer,T> clos) {
		for (int i = 0; i < arr.length; i++)
			clos.invoke(i, (T)arr[i]);
		return null;
	}

	@SuppressWarnings("unchecked")
	public final <T> int iteriUntil(Object[] arr, Func2<Boolean,Integer,T> clos) {
		for (int i = 0; i < arr.length; i++)
			if (clos.invoke(i, (T)arr[i]))
				return i;
		return arr.length;
	}

	@SuppressWarnings("unchecked")
	public final <T1,T2> T1 fold(Object[] arr, T1 init, Func2<T1,T1,T2> clos) {
		for (int i = 0; i < arr.length; i++)
			init = clos.invoke(init, (T2)arr[i]);
		return init;
	}

	@SuppressWarnings("unchecked")
	public final <T1,T2> T1 foldi(Object[] arr, T1 init, Func3<T1,Integer,T1,T2> clos) {
		for (int i = 0; i < arr.length; i++)
			init = clos.invoke(i, init, (T2)arr[i]);
		return init;
	}

	@SuppressWarnings("unchecked")
	public final <T> Object[] filter(Object[] arr, Func1<Boolean,T> test) {
		boolean[] tmp = new boolean[arr.length];
		int count = 0;
		for (int i = 0; i < arr.length; i++)
			if (tmp[i] = test.invoke((T)arr[i]))
				count++;
		Object[] out = new Object[count];
		for (int i = 0, j = 0; i < arr.length; i++)
			if (tmp[i])
				out[j++] = arr[i];
		return out;
	}

	@SuppressWarnings("unchecked")
	public final <T> boolean exists(Object[] arr, Func1<Boolean,T> test) {
		for (int i = 0; i < arr.length; i++)
			if (test.invoke((T)arr[i]))
				return true;
		return false;
	}

	public final double random() {
		return Math.random();
	}

	public final Func0<Double> randomGenerator(Integer seed) {
		return new Func0<Double>() {
			Random generator = new Random(seed);
			public Double invoke() {
				return generator.nextDouble();
			}
		};
	}

	private Map<Long, Timer> timers = new HashMap<Long, Timer>();

	private Timer getTimer() {
		Long threadId = Thread.currentThread().getId();
		if (timers.containsKey(threadId)) {
			return timers.get(threadId);
		} else {
			Timer timer = new Timer(true);
			synchronized (timers) {
				timers.put(threadId, timer);
			}

			TimerTask task = new TimerTask() {
				public void run() {
					invokeCallback(new Runnable() {
						public void run() {
							synchronized (timers) {
								timers.put(Thread.currentThread().getId(), timer);
							}
						}
					});
				}
			};
			timer.schedule(task, 0);

			return timer;
		}
	}

	public void invokeCallback(Runnable cb) {
		cb.run();
	}

	public final Object timer(int ms, final Func0<Object> cb) {
		Timer timer = getTimer();
		TimerTask task = new TimerTask() {
			public void run() {
				invokeCallback(new Runnable() {
					public void run() {
						cb.invoke();
					}
				});
			}
		};
		timer.schedule(task, ms);

		return null;
	}

	public final Object sustainableTimer(Integer ms, final Func0<Object> cb) {
		Timer timer = new Timer(false);
		TimerTask task = new TimerTask() {
			public void run() {
				invokeCallback(new Runnable() {
					public void run() {
						cb.invoke();
						timer.cancel();
					}
				});
			}
		};
		timer.schedule(task, ms);
		return null;
	}

	public final Func0<Object> interruptibleTimer(int ms, final Func0<Object> cb) {
		Timer timer = getTimer();
		TimerTask task = new TimerTask() {
			public void run() {
				invokeCallback(new Runnable() {
					public void run() {
						cb.invoke();
					}
				});
			}
		};
		timer.schedule(task, ms);

		return new Func0<Object>() {
			public Object invoke() {
				timer.cancel();
				return null;
			}
		};
	}

	public final double sin(double a) {
		return Math.sin(a);
	}

	public final double asin(double a) {
		return Math.asin(a);
	}

	public final double acos(double a) {
		return Math.acos(a);
	}

	public final double atan(double a) {
		return Math.atan(a);
	}

	public final double atan2(double a, double b) {
		return Math.atan2(a, b);
	}

	public final double exp(double a) {
		return Math.exp(a);
	}

	public final double log(double a) {
		return Math.log(a);
	}

	public final Object[] enumFromTo(int from, int to) {
		int n = to - from + 1;
		if (n < 0)
			return new Object[0];
		Object[] rv = new Object[n];
		for (int i = 0; i < n; i++)
			rv[i] = from + i;
		return rv;
	}

	public final double timestamp() {
		return System.currentTimeMillis();
	}

	public Object[][] getAllUrlParameters() {
		String[] args = runtime.getUrlArgs();

		Object[][] parameters = new Object[args.length][2];

		for (int i = 0; i < args.length; i++) {
			String p = args[i];

			int pos = p.indexOf("=");
			if (pos >= 0) {
				parameters[i][0] = p.substring(0, pos);
				parameters[i][1] = p.substring(pos + 1);
			} else {
				parameters[i][0] = p;
				parameters[i][1] = "";
			}
		}

		return parameters;
	}

	public String getUrlParameter(String name) {
		String[] args = runtime.getUrlArgs();

		for (String p : args) {
			if (p.startsWith(name + "=")) {
				String arg = p.substring(name.length() + 1);
				return arg;
			}
		}

		return "";
	}

	public final String loaderUrl() {
		return "";
	}

	public final String getTargetName() {
		String osName = System.getProperty("os.name").toLowerCase();
		int space_ind = osName.indexOf(" ");
		osName = osName.substring(0, space_ind == -1 ? osName.length() : space_ind);
		return  osName + ",java";
	}

	public final boolean setKeyValue(String k, String v) {
		return false;
	}

	public final String getKeyValue(String k, String def) {
		return def;
	}

	public final Object removeKeyValue(String k) {
		return null;
	}

	public final Object removeAllKeyValues() {
		return null;
	}

	public final Object[] getKeysList() {
		return new Object[0];
	}

	public final Object clearTrace() {
		return null;
	}

	public final Object printCallstack() {
		Thread.dumpStack();
		return null;
	}

	public final Object captureCallstack() {
		return Thread.currentThread().getStackTrace();
	}
	public final String captureStringCallstack() {
		return callstack2string(captureCallstack());
	}
	public final String callstack2string(Object obj) {
		if (obj instanceof StackTraceElement[]) {
			StackTraceElement[] stack = (StackTraceElement[])obj;
			StringBuilder sb = new StringBuilder();
			for (StackTraceElement el : stack) {
				sb.append(el.toString() + "\n");
			}
			return sb.toString();
		} else {
			return new String();
		}
	}
	public final Object captureCallstackItem(int index) {
		return Thread.currentThread().getStackTrace()[index];
	}
	public final Object impersonateCallstackItem(Object item, int index) {
		return null;
	}
	public final Object impersonateCallstackFn(Object item, int index) {
		return null;
	}
	public final Object impersonateCallstackNone(int index) {
		return null;
	}

	public final Object makeStructValue(String name, Object[] args, Object defval) {
		return runtime.makeStructValue(name, args, (Struct)defval);
	}

	public final Object[] extractStructArguments(Object val) {
		if (val instanceof Struct) {
			return ((Struct) val).getFields();
		} else return new Object[0];
	}


	public final Object quit(int c) {
		System.exit(c);
		return null;
	}

	public final String fromCharCode(int c) {
		return new String(new char[] { (char)c });
	}

	public final int getCharCodeAt(String s, int i) {
		return (i>=0 && i < s.length()) ? (int)s.charAt(i) : -1;
	}

	public final double number2double(Object n) {
		return ((Number)n).doubleValue();
	}

	public final Struct getCurrentDate() {
		GregorianCalendar date = new GregorianCalendar();
		return runtime.makeStructValue(
				"Date",
				new Object[] {
					date.get(Calendar.YEAR),
					date.get(Calendar.MONTH) + 1,
					date.get(Calendar.DAY_OF_MONTH)
				},
				null
			);
	}

	// Monday is 0
	public final int dayOfWeek(int year, int month, int day) {
		Calendar c = Calendar.getInstance();
		c.set(year, month - 1, day);
		return (c.get(Calendar.DAY_OF_WEEK) - (Calendar.SUNDAY + 1) + 7) % 7;
	}

	public final double utc2local(double stamp) {
		// TODO
		return 0;
	}

	public final double local2utc(double stamp) {
		// TODO
		return 0;
	}

	static private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyy-MM-dd HH:mm:ss");

	public final String time2string(double time) {
		long millis = Double.valueOf(time).longValue();
		return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(dateFormat);
	}

	public final double string2time(String tv) {
		try {
			return LocalDateTime.parse(tv, dateFormat).toInstant(ZoneOffset.ofHours(0)).toEpochMilli();
		} catch (DateTimeParseException  e) {
			System.err.println(e.toString());
			return 0;
		}
	}

	public final String getUrl(String u, String t) {
		// TODO
		return "";
	}

	public final String getFileContent(String name) {
		String result = "";
		try {
			byte[] bytes = Files.readAllBytes(Paths.get(name));
			result = utf82string(bytes);
		} catch (IOException e) {
		} catch (InvalidPathException e) {
		}
		return result;
	}

	public final byte[] string2utf8Bytes(String data) {
		Object[] intsArray = string2utf8(data);
		byte[] bytesArray = new byte[intsArray.length];
		for(int i = 0; i < intsArray.length; i++) {
			Integer a =  (Integer)intsArray[i];
			// We know that 'a' is already a byte,
			// so instead of using expensive '.valueOf()'
			// use cheaper '% 256'.
			// int b = Integer.valueOf(a);
			int b = a % 256;
			byte c = (byte)b;

			bytesArray[i] = c;//(byte)(Integer.valueOf((Integer)bytes[i]));
		}
		return bytesArray;
	}

	public final boolean setFileContent(String name, String data) {
		try {
			byte[] bytes = string2utf8Bytes(data);
			Files.write(Paths.get(name), bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		} catch (IOException ex) {
			return false;
		}
		return true;
	}

	public final boolean setFileContentUTF16(String name, String data) {
		Writer writer = null;

		try {
			writer = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(name), "UTF-16LE")
			);
			writer.write('\ufeff' + data);
		} catch (IOException ex) {
		} finally {
			try {
				writer.close();
				return true;
			} catch (Exception ex) {}
		}
		return false;
	}


	public final String getFileContentBinary(String name) {
		try {
			byte[] bytes = Files.readAllBytes(Paths.get(name));
			return new String(bytes, StandardCharsets.ISO_8859_1);
		} catch (IOException e) {
			return "";
		}
	}

	public final boolean setFileContentBytes(String name, String data) {
		Writer writer = null;

		try {
			writer = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(name), StandardCharsets.ISO_8859_1)
			);
			char[] bytes = new char[data.length()];
			for (int i = 0; i < bytes.length; i++) {
				int cp =  Character.codePointAt(data, i);
				bytes[i] = (char)(cp % 256);
			}
			writer.write(bytes);
		} catch (IOException ex) {
		} finally {
			try {
				writer.close();
				return true;
			} catch (Exception ex) {}
		}
		return false;
	}

	public final boolean setFileContentBinary(String name, String data) {
		Writer writer = null;

		try {
			writer = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(name), StandardCharsets.ISO_8859_1)
			);
			char[] bytes = new char[data.length()];
			for (int i = 0; i < bytes.length; i++) {
				int cp =  Character.codePointAt(data, i / 2);
				bytes[i] = (char)((i%2 == 0) ? (cp % 256) : ((cp >> 8) % 256));
			}
			writer.write(bytes);
		} catch (IOException ex) {
		} finally {
			try {
				writer.close();
				return true;
			} catch (Exception ex) {}
		}
		return false;
	}

	public final Object fast_max(Object aa, Object ab) {
		// Flow uses generic version of max(), which fallback
		// to compareByValue(). Add special cases for int and double.
		// Got ~1-2% of performance.
		if ((aa instanceof Double) && (ab instanceof Double)) {
			if ((Double) aa > (Double) ab) return aa; else return ab;
		} else if ((aa instanceof Integer) && (ab instanceof Integer)) {
			if ((Integer) aa > (Integer) ab) return aa; else return ab;
		} else if ((FlowRuntime.compareByValue(aa,ab)>0))
			return aa; else return ab;
	}

	private final static String exceptionStackTrace(Exception ex) {
		StringWriter stackTrace = new StringWriter();
		ex.printStackTrace(new PrintWriter(stackTrace));
		return stackTrace.toString();
	}

	private final class ProcessRunner implements Runnable {

		private final String[] cmd;
		private final String cwd;
		private final String stdin;
		private final Func3<Object, Integer, String, String> onExit;

		public ProcessRunner(String[] cmd, String cwd, String stdin, Func3<Object, Integer, String, String> onExit) {
			this.cmd = cmd;
			this.cwd = cwd;
			this.onExit = onExit;
			this.stdin = stdin;
		}

		private class StreamReader implements Runnable {
			String name;
			InputStream is;
			String contents;
			Thread thread;
			StreamReader errReader;

			public StreamReader(String name, InputStream is) {
				this.name = name;
				this.is = is;
				errReader = this;
				contents = new String();
				thread = new Thread(this);
				thread.start();
			}
			public StreamReader(String name, InputStream is, StreamReader errReader) {
				this.name = name;
				this.is = is;
				this.errReader = errReader;
				contents = new String();
				thread = new Thread(this);
				thread.start();
			}
			public void run() {
				try {
					InputStreamReader isr = new InputStreamReader(is);
					BufferedReader br = new BufferedReader(isr);
					while (!thread.isInterrupted()) {
						String s = br.readLine();
						if (s == null) break;
						contents += s + "\n";
					}
				} catch (Exception ex) {
					errReader.contents += exceptionStackTrace(ex) + "\n";
				}
			}
			public void close() {
				thread.interrupt();
				try {
					is.close();
				} catch (Exception ex) {
					errReader.contents += exceptionStackTrace(ex) + "\n";
				}
			}
		}

		@Override
		public void run() {
			StreamReader stderr = null;
			StreamReader stdout = null;
			try {
				Process process = Runtime.getRuntime().exec(this.cmd, null, new File(this.cwd));
				stderr = new StreamReader("stderr", process.getErrorStream());
				stdout = new StreamReader("stdout", process.getInputStream(), stderr);

				process.getOutputStream().write(this.stdin.getBytes());
				process.getOutputStream().flush();

				// We wait for the process to finish before we collect the output!
				process.waitFor();

				stdout.close();
				stderr.close();
				onExit.invoke(process.exitValue(), stdout.contents, stderr.contents);
			} catch (Exception ex) {
				String cmd_str = "";
				for (String c : this.cmd) {
					cmd_str += c + " ";
				}
				String err_str = "";
				if (stderr != null) {
					err_str += stderr.contents + "\n";
				}
				err_str += "while executing:\n" + cmd_str + "\n";
				err_str += exceptionStackTrace(ex);
				onExit.invoke(-200, "", err_str);
			}
		}
	}

	public final String md5(String contents) {
		MessageDigest messageDigest = null;
		byte[] digest = new byte[0];

		try {
			if (md5original != null) {
			messageDigest = (MessageDigest) md5original.clone();
			messageDigest.reset();
			messageDigest.update(contents.getBytes("UTF-8"));
			digest = messageDigest.digest();
			} else {
			return "";
			}
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

		BigInteger bigInt = new BigInteger(1, digest);
		String md5Hex = bigInt.toString(16);

		while( md5Hex.length() < 32 ){
			md5Hex = "0" + md5Hex;
		}

		return md5Hex;
	}

	public String fileChecksum(String filename) {
		try {
			InputStream fis =  new FileInputStream(filename);
			byte[] buffer = new byte[1024];
			MessageDigest md = MessageDigest.getInstance("MD5");
			int numRead;
			do {
				numRead = fis.read(buffer);
				if (numRead > 0) {
					md.update(buffer, 0, numRead);
				}
			} while (numRead != -1);

			fis.close();

			byte[] digest = new byte[0];
			digest = md.digest();

			BigInteger bigInt = new BigInteger(1, digest);
			String md5Hex = bigInt.toString(16);

			while( md5Hex.length() < 32 ){
				md5Hex = "0" + md5Hex;
			}

			return md5Hex;
		} catch (IOException e) {
			return "";
		} catch (InvalidPathException e) {
			return "";
		} catch (Exception e) {
			e.printStackTrace();
			return "";
		}
	}

	// Launch a system process
	public final Object startProcess(String command, Object[] args, String currentWorkingDirectory, String stdin,
					 Func3<Object, Integer, String, String> onExit) {

	try {
		String[] cmd = new String[args.length + 1];
		cmd[0] = command;
		for (int i = 0; i < args.length; i++) {
		cmd[i+1] = (String)args[i];
		}

		ProcessRunner ps = new ProcessRunner(cmd, currentWorkingDirectory, stdin, onExit);
		Future future = threadpool.submit(ps);

		return true;
	} catch (Exception ex) {
		onExit.invoke(-200, "", "while starting:\n" + command + "\noccured:\n" + exceptionStackTrace(ex));
		return false;
	}
	}

	private final class ProcessStarter implements Runnable {

		private final String[] cmd;
		private final String cwd;
		private final Func1<Object, String> onOut;
		private final Func1<Object, String> onErr;
		private final Func1<Object, Integer> onExit;
		private StreamReader stdout;
		private StreamReader stderr;
		private ExitHandler  exit;
		private Process process;

		public ProcessStarter(
			String[] cmd,
			String cwd,
			Func1<Object, String> onOut,
			Func1<Object, String> onErr,
			Func1<Object, Integer> onExit
		) {
			this.cmd = cmd;
			this.cwd = cwd;
			this.onOut = onOut;
			this.onErr = onErr;
			this.onExit = onExit;
		}

		private class StreamReader implements Runnable {
			String name;
			InputStream is;
			Thread thread;
			private final Func1<Object, String> callback;
			private final Func1<Object, String> onErr;

			public StreamReader(String name, InputStream is, Func1<Object, String> callback, Func1<Object, String> onErr) {
				this.name = name;
				this.is = is;
				this.callback = callback;
				this.onErr = onErr;
				thread = new Thread(this);
				thread.start();
			}
			public void run() {
				try {
					InputStreamReader isr = new InputStreamReader(is);
					BufferedReader br = new BufferedReader(isr);
					while (!thread.isInterrupted()) {
						String s = br.readLine();
						if (s == null) break;
						callback.invoke(s);
					}
				} catch (Exception ex) {
					onErr.invoke("Problem reading stream " + name + ":\n" + exceptionStackTrace(ex));
				}
			}
			public void close() {
				thread.interrupt();
				try {
					is.close();
				} catch (Exception ex) {
					onErr.invoke("Problem closing stream " + name + ":\n" + exceptionStackTrace(ex));
				}
			}
		}

		private class ExitHandler implements Runnable {
			Process process;
			Thread thread;
			StreamReader out;
			StreamReader err;
			private final Func1<Object, Integer> callback;
			private final Func1<Object, String> onErr;

			public ExitHandler(Process process, Func1<Object, Integer> callback, Func1<Object, String> onErr, StreamReader out, StreamReader err) {
				this.process = process;
				this.callback = callback;
				this.out = out;
				this.err = err;
				this.onErr = onErr;
				thread = new Thread(this);
				thread.start();
			}
			public void run() {
				try {
					while (process.isAlive()) {
						thread.sleep(250);
					}
					err.close();
					out.close();
					callback.invoke(process.exitValue());
				} catch (InterruptedException ex) {
					onErr.invoke(exceptionStackTrace(ex));
				}
			}
		}

		public void writeStdin(String in) {
			try {
				if (process != null && process.isAlive()) {
					process.getOutputStream().write(in.getBytes());
					process.getOutputStream().flush();
				}
			} catch (IOException ex) {
				onErr.invoke(exceptionStackTrace(ex));
			}
		}

		public void kill() {
			try {
				stdout.close();
				stderr.close();
				process.waitFor(100, TimeUnit.MILLISECONDS);
				if (process != null && process.isAlive()) {
					process.destroy();
					process.waitFor(250, TimeUnit.MILLISECONDS);
					if (process.isAlive()) {
						process.destroyForcibly();
						process.waitFor();
					}
				}
				process = null;
			} catch (InterruptedException ex) {
				onErr.invoke(exceptionStackTrace(ex));
			}
		}

		@Override
		public void run() {
			try {
				process = Runtime.getRuntime().exec(this.cmd, null, new File(this.cwd));
				stdout = new StreamReader("stdout", process.getInputStream(), onOut, onOut);
				stderr = new StreamReader("stderr", process.getErrorStream(), onErr, onOut);
				exit   = new ExitHandler(process, onExit, onErr, stdout, stderr);
			} catch (IOException ex) {
				String cmd_str = "";
				for (String c : this.cmd) {
					cmd_str += c + " ";
				}
				onErr.invoke("while executing:\n" + cmd_str + "\n" + exceptionStackTrace(ex));
				onExit.invoke(-200);
			}
		}

		public int waitFor() {
			try {
				if (stdout != null && stdout.thread != null) {
					stdout.thread.join();
				}
				if (stderr != null && stderr.thread != null) {
					stderr.thread.join();
				}
				if (exit != null && exit.thread != null) {
					exit.thread.join();
				}
				if (process != null) {
					return process.waitFor();
				} else {
					return 0;
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
				return 1;
			}
		}
	}

	public final Object runSystemProcess(String command, Object[] args, String currentWorkingDirectory,
					Func1<Object, String> onOut, Func1<Object, String> onErr, Func1<Object, Integer> onExit) {
		try {
			String[] cmd = new String[args.length + 1];
			cmd[0] = command;
			for (int i = 0; i < args.length; i++) {
				cmd[i+1] = (String)args[i];
			}
			ProcessStarter runner = new ProcessStarter(cmd, currentWorkingDirectory, onOut, onErr, onExit);
			Future future = threadpool.submit(runner);

			return runner;
		} catch (Exception ex) {
			onErr.invoke("while starting:\n" + command + "\noccured:\n" + exceptionStackTrace(ex));
			onExit.invoke(-200);
			return null;
		}
	}

	public final int execSystemProcess(String command, Object[] args, String currentWorkingDirectory,
					Func1<Object, String> onOut, Func1<Object, String> onErr) {
		try {
			String[] cmd = new String[args.length + 1];
			cmd[0] = command;
			for (int i = 0; i < args.length; i++) {
				cmd[i+1] = (String)args[i];
			}
			ProcessStarter runner = new ProcessStarter(cmd, currentWorkingDirectory, onOut, onErr,
				new Func1<Object, Integer>()  {
					@Override
					public Object invoke(Integer code) { return null; }
				}
			);
			runner.run();
			return runner.waitFor();
		} catch (Exception ex) {
			onErr.invoke("while execution of:\n" + command + "\noccured:\n" + exceptionStackTrace(ex));
			return 1;
		}
	}

	public final Object writeProcessStdin(Object process, String arg) {
		((ProcessStarter)process).writeStdin(arg);
		return null;
	}

	public final Object killProcess(Object process) {
		((ProcessStarter)process).kill();
		return null;
	}

	public final boolean startDetachedProcess(String command, Object[] args, String currentWorkingDirectory) {
		return false;
	}

	public final Object[] concurrent(Boolean fine, Object[] tasks) {

	  List<Callable<Object>> tasks2 = new ArrayList<Callable<Object>>();

	  for (int i = 0; i < tasks.length; i++) {
		@SuppressWarnings("unchecked")
		Func0<Object> task = (Func0<Object>) tasks[i];
		tasks2.add(new Callable<Object>() {
		  @Override
		  public Object call() throws Exception {
			  try {
				return task.invoke();
			  } catch (OutOfMemoryError e) {
				// This is brutal, but there is no memory to print anything
				// so better to stop than to hang in infinite loop.
				System.exit(255);
				return null;
			  }
			}
		});
	  }

	  Object[] resArr = new Object[0];

	  try {
		List<Object> res = new ArrayList<Object>();
		for (Future<Object> future : threadpool.invokeAll(tasks2)) {
		  res.add(future.get());
		}
		resArr = res.toArray();
	  } catch (InterruptedException e) {
		e.printStackTrace();
	  } catch (ExecutionException e) {
		e.printStackTrace();
	  }

	  return resArr;
	}

	public final Object concurrentAsyncCallback(
		Func2<Object, String, Func1<Object, Object>> task,
		Func1<Object, Object> onDone,
		Func1<Object, String> onFail
	) {
		// thread #1
		CompletableFuture.supplyAsync(() -> {
			// thread #2
			CompletableFuture<Object> completableFuture = new CompletableFuture<Object>();
			String threadId = Long.toString(Thread.currentThread().getId());
			try {
				task.invoke(threadId, (res) -> {
					// thread #2
					completableFuture.complete(res);
					return null;
				});
			} catch (Exception e) {
				return onFail.invoke("Thread #" + threadId + " failed: " + e.getMessage());
			}
			Object result = null;
			try {
				result = completableFuture.get();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
			return result;
		}, threadpool).thenApply(result -> {
			// thread #2
			return onDone.invoke(result);
		});

		return null;
	}

	public final String getThreadId() {
		return Long.toString(Thread.currentThread().getId());
	}

	public final Object initConcurrentHashMap() {
		return new ConcurrentHashMap();
	}

	public final Object setConcurrentHashMap(Object map, Object key, Object value) {
		@SuppressWarnings("unchecked")
		ConcurrentHashMap<Object, Object> concurrentMap = (ConcurrentHashMap<Object, Object>) map;
		concurrentMap.put(key, value);
		return null;
	}

	public final Object getConcurrentHashMap(Object map, Object key, Object defval) {
		ConcurrentHashMap concurrentMap = (ConcurrentHashMap) map;
		return concurrentMap.containsKey(key) ? concurrentMap.get(key) : defval;
	}

	@SuppressWarnings("unchecked")
	public final Object setAllConcurrentHashMap(Object map1, Object map2) {
		ConcurrentHashMap<Object, Object> concurrentMap1 = (ConcurrentHashMap<Object, Object>) map1;
		ConcurrentHashMap<Object, Object> concurrentMap2 = (ConcurrentHashMap<Object, Object>) map2;
		concurrentMap1.putAll(concurrentMap2);
		return null;
	}

	public final Boolean containsConcurrentHashMap(Object map, Object key) {
		ConcurrentHashMap concurrentMap = (ConcurrentHashMap) map;
		return concurrentMap.containsKey(key);
	}

	public final Object[] valuesConcurrentHashMap(Object map) {
		ConcurrentHashMap concurrentMap = (ConcurrentHashMap) map;
		return concurrentMap.values().toArray();
	}

	public final Object removeConcurrentHashMap(Object map, Object key) {
		ConcurrentHashMap concurrentMap = (ConcurrentHashMap) map;
		concurrentMap.remove(key);
		return null;
	}

	public final Object[] keysConcurrentHashMap(Object map) {
		@SuppressWarnings("unchecked")
		ConcurrentHashMap<Object, Object> concurrentMap = (ConcurrentHashMap<Object, Object>) map;
		ArrayList<Object> ret = new ArrayList<Object>();
		for (Enumeration<Object> e = concurrentMap.keys(); e.hasMoreElements();) {
			ret.add(e.nextElement());
		}
		return ret.toArray();
	}

	public final int sizeConcurrentHashMap(Object map) {
		ConcurrentHashMap concurrentMap = (ConcurrentHashMap) map;
		return concurrentMap.size();
	}

	public final Object clearConcurrentHashMap(Object map) {
		ConcurrentHashMap concurrentMap = (ConcurrentHashMap) map;
		concurrentMap.clear();
		return null;
	}

	// TODO: why don't we use threadpool here?
	public final Object concurrentAsyncOne(Boolean fine, Func0<Object> task, Func1<Object,Object> callback) {
		CompletableFuture.supplyAsync(() -> {
			return task.invoke();
		}).thenApply(result -> {
			return callback.invoke(result);
		});
		return null;
	}

	public synchronized final int atomicRefIntAddition(Reference<Integer> rv, Integer delta) {
	  int result = rv.value;
	  rv.value = result + delta;
	  return result;
	}

	public final Func0<Object> addCameraPhotoEventListener(Func5<Object, Integer, String, String, Integer, Integer> cb) {
		// not implemented yet for java
		return null;
	}
	public final Func0<Object> addCameraVideoEventListener(Func5<Object, Integer, String, String, Integer, Integer> cb) {
		// not implemented yet for java
		return null;
	}
	//native addPlatformEventListenerNative : (event : string, cb : () -> bool) -> ( () -> void ) = Native.addPlatformEventListener;
	public final Func0<Object> addPlatformEventListener (String event, Func0<Boolean> cb) {
	return null;
	}

	public final int availableProcessors() {
		return Runtime.getRuntime().availableProcessors();
	}

	public final Object setThreadPoolSize(int threads) {
		threadpool = Executors.newFixedThreadPool(threads);
		return null;
	}

	public final String readBytes(int n) {
		byte[] input = new byte[n];
		try {
			int have_read = 0;
			while (have_read < n) {
				int read_bytes = System.in.read(input, have_read, n - have_read);
				if (read_bytes == -1) {
					break;
				}
				have_read += read_bytes;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			return new String(input, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			return new String();
		}
	}

	public final String readUntil(String str_pattern) {
		byte[] pattern = str_pattern.getBytes();
		ArrayList<Byte> line = new ArrayList<Byte>();
		int pos = 0;
		try {
			while (true) {
				int ch = System.in.read();
				line.add(Byte.valueOf((byte)ch));
				if (ch == pattern[pos]) {
					pos += 1;
					if (pos == pattern.length) {
						break;
					}
				} else {
					pos = 0;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		byte[] bytes = new byte[line.size()];
		for (int i = 0; i < line.size(); ++ i) {
			bytes[i] = line.get(i).byteValue();
		}
		try {
			return new String(bytes, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			return new String();
		}
	}

	public final Object print(String s) {
		try{
			synchronized (System.out) {
				PrintStream out = new PrintStream(System.out, true, "UTF-8");
				out.print(s);
				out.flush();
			}
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return null;
	}

	// Memory statistics:
	public final double totalMemory() {
		return (double)(Runtime.getRuntime().totalMemory());
	}
	public final double freeMemory() {
		return (double)(Runtime.getRuntime().freeMemory());
	}
	public final double maxMemory() {
		return (double)(Runtime.getRuntime().maxMemory());
	}

	// CPU load
	public final double getProcessCpuLoad() {
		return osBean.getProcessCpuLoad();
	}

	// Vector natives:
	public final Object makeVector(Integer capacity) {
		return new ArrayList(capacity);
	}
	public final Object getVector(Object v, Integer i) {
		ArrayList vector = (ArrayList)v;
		return vector.get(i.intValue());
	}
	public final Object setVector(Object v, Integer i, Object x) {
		@SuppressWarnings("unchecked")
		ArrayList<Object> vector = (ArrayList<Object>)v;
		vector.set(i.intValue(), x);
		return null;
	}
	public final Object addVector(Object v, Object x) {
		@SuppressWarnings("unchecked")
		ArrayList<Object> vector = (ArrayList<Object>)v;
		vector.add(x);
		return null;
	}
	public final Object removeVector(Object v, Integer i) {
		ArrayList vector = (ArrayList)v;
		vector.remove(i.intValue());
		return null;
	}
	public final int sizeVector(Object v) {
		ArrayList vector = (ArrayList)v;
		return vector.size();
	}
	public final Object clearVector(Object v) {
		ArrayList vector = (ArrayList)v;
		vector.clear();
		return null;
	}
	public final Object shrinkVector(Object v, Integer size) {
		ArrayList vector = (ArrayList)v;
		int i = vector.size();
		while (i > size) {
			vector.remove(--i);
		}
		return null;
	}
	public final Object subVector(Object v, Integer index, Integer len) {
		@SuppressWarnings("unchecked")
		ArrayList<Object> vector = (ArrayList<Object>)v;
		ArrayList<Object> sub = new ArrayList<Object>(len);
		for (int i = index; i < index + len; ++ i) {
			sub.add(vector.get(i));
		}
		return sub;
	}
	public final Object[] vector2array(Object v) {
		ArrayList vector = (ArrayList)v;
		return vector.toArray();
	}
	public final Object array2vector(Object[] a) {
		return new ArrayList<Object>(Arrays.asList(a));
	}

	public final <RT> Func0<RT> synchronizedConstFn(Object lock, Func0<RT> fn) {
		return new Func0<RT>() {
			@Override
			public RT invoke() {
				synchronized (lock) {
					return fn.invoke();
				}
			}
		};
	}
	public final <RT, A1> Func1<RT, A1> synchronizedUnaryFn(Object lock, Func1<RT, A1> fn) {
		return new Func1<RT, A1>() {
			@Override
			public RT invoke(A1 arg1) {
				synchronized (lock) {
					return fn.invoke(arg1);
				}
			}
		};
	}
	public final <RT, A1, A2> Func2<RT, A1, A2> synchronizedBinaryFn(Object lock, Func2<RT, A1, A2> fn) {
		return new Func2<RT, A1, A2>() {
			@Override
			public RT invoke(A1 arg1, A2 arg2) {
				synchronized (lock) {
					return fn.invoke(arg1, arg2);
				}
			}
		};
	}

	public final String urlDecode(String s) {
		try {
			return URLDecoder.decode(s, "UTF-8");
		} catch (UnsupportedEncodingException | IllegalArgumentException e) {
			System.out.println(e.toString());
			return "";
		}
	}
}
