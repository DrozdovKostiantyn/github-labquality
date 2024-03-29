package org.cliffc.high_scale_lib;

/*
 * Written by Cliff Click and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import junit.framework.TestCase;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

// Test NonBlockingHashMap via JUnit
public class NonBlockingHashMapTest extends TestCase {

  private NonBlockingHashMap<String,String> _nbhm;
  protected void setUp   () { _nbhm = new NonBlockingHashMap<String,String>(); }
  protected void tearDown() { _nbhm = null; }

  // Throw a ClassCastException if I see a tombstone during key-compares
  private static class KeyBonk {
    final int _x;
    KeyBonk( int i ) { _x=i; }
    public boolean equals( Object o ) {
      if( o == null ) return false;
      return ((KeyBonk)o)._x    // Throw CCE here
        == this._x; 
    }
    public int hashCode() { return (_x>>2); }
    public String toString() { return "Bonk_"+Integer.toString(_x); }
  }

  // Test some basic stuff; add a few keys, remove a few keys
  public void testBasic() {
    assertTrue ( _nbhm.isEmpty() );
    assertThat ( _nbhm.putIfAbsent("k1","v1"), nullValue() );
    checkSizes (1);
    assertThat ( _nbhm.putIfAbsent("k2","v2"), nullValue() );
    checkSizes (2);
    assertTrue ( _nbhm.containsKey("k2") );
    assertThat ( _nbhm.put("k1","v1a"), is("v1") );
    assertThat ( _nbhm.put("k2","v2a"), is("v2") );
    checkSizes (2);
    assertThat ( _nbhm.putIfAbsent("k2","v2b"), is("v2a") );
    assertThat ( _nbhm.remove("k1"), is("v1a") );
    assertFalse( _nbhm.containsKey("k1") );
    checkSizes (1);
    assertThat ( _nbhm.remove("k1"), nullValue() );
    assertThat ( _nbhm.remove("k2"), is("v2a") );
    checkSizes (0);
    assertThat ( _nbhm.remove("k2"), nullValue() );
    assertThat ( _nbhm.remove("k3"), nullValue() );
    assertTrue ( _nbhm.isEmpty() );

    assertThat ( _nbhm.put("k0","v0"), nullValue() );
    assertTrue ( _nbhm.containsKey("k0") );
    checkSizes (1);
    assertThat ( _nbhm.remove("k0"), is("v0") );
    assertFalse( _nbhm.containsKey("k0") );
    checkSizes (0);

    assertThat ( _nbhm.replace("k0","v0"), nullValue() );
    assertFalse( _nbhm.containsKey("k0") );
    assertThat ( _nbhm.put("k0","v0"), nullValue() );
    assertEquals(_nbhm.replace("k0","v0a"), "v0" );
    assertEquals(_nbhm.get("k0"), "v0a" );
    assertThat ( _nbhm.remove("k0"), is("v0a") );
    assertFalse( _nbhm.containsKey("k0") );
    checkSizes (0);

    assertThat ( _nbhm.replace("k1","v1"), nullValue() );
    assertFalse( _nbhm.containsKey("k1") );
    assertThat ( _nbhm.put("k1","v1"), nullValue() );
    assertEquals(_nbhm.replace("k1","v1a"), "v1" );
    assertEquals(_nbhm.get("k1"), "v1a" );
    assertThat ( _nbhm.remove("k1"), is("v1a") );
    assertFalse( _nbhm.containsKey("k1") );
    checkSizes (0);

    // Insert & Remove KeyBonks until the table resizes and we start
    // finding Tombstone keys- and KeyBonk's equals-call with throw a
    // ClassCastException if it sees a non-KeyBonk.
    NonBlockingHashMap<KeyBonk,String> dumb = new NonBlockingHashMap<KeyBonk,String>();
    for( int i=0; i<10000; i++ ) {
      final KeyBonk happy1 = new KeyBonk(i);
      assertThat( dumb.put(happy1,"and"), nullValue() );
      if( (i&1)==0 )  dumb.remove(happy1);
      final KeyBonk happy2 = new KeyBonk(i); // 'equals' but not '=='
      dumb.get(happy2);
    }

    // Simple insert of simple keys, with no reprobing on insert until the
    // table gets full exactly.  Then do a 'get' on the totally full table.
    NonBlockingHashMap<Integer,Object> map = new NonBlockingHashMap<Integer,Object>(32);
    for( int i = 1; i < 32; i++ )
      map.put(i, new Object());
    map.get(33);  // this causes a NPE
  }

  // Check all iterators for correct size counts
  private void checkSizes(int expectedSize) {
    assertEquals( "size()", _nbhm.size(), expectedSize );
    Collection<String> vals = _nbhm.values();
    checkSizes("values()",vals.size(),vals.iterator(),expectedSize);
    Set<String> keys = _nbhm.keySet();
    checkSizes("keySet()",keys.size(),keys.iterator(),expectedSize);
    Set<Entry<String,String>> ents = _nbhm.entrySet();
    checkSizes("entrySet()",ents.size(),ents.iterator(),expectedSize);
  }

  // Check that the iterator iterates the correct number of times
  private void checkSizes(String msg, int sz, Iterator it, int expectedSize) {
    assertEquals( msg, expectedSize, sz );
    int result = 0;
    while (it.hasNext()) {
      result++;
      it.next();
    }
    assertEquals( msg, expectedSize, result );
  }


  public void testIteration() {
    assertTrue ( _nbhm.isEmpty() );
    assertThat ( _nbhm.put("k1","v1"), nullValue() );
    assertThat ( _nbhm.put("k2","v2"), nullValue() );

    String str1 = "";
    for( Iterator<Map.Entry<String,String>> i = _nbhm.entrySet().iterator(); i.hasNext(); ) {
      Map.Entry<String,String> e = i.next();
      str1 += e.getKey();
    }
    assertThat("found all entries",str1,anyOf(is("k1k2"),is("k2k1")));

    String str2 = "";
    for( Iterator<String> i = _nbhm.keySet().iterator(); i.hasNext(); ) {
      String key = i.next();
      str2 += key;
    }
    assertThat("found all keys",str2,anyOf(is("k1k2"),is("k2k1")));

    String str3 = "";
    for( Iterator<String> i = _nbhm.values().iterator(); i.hasNext(); ) {
      String val = i.next();
      str3 += val;
    }
    assertThat("found all vals",str3,anyOf(is("v1v2"),is("v2v1")));

    assertThat("toString works",_nbhm.toString(), anyOf(is("{k1=v1, k2=v2}"),is("{k2=v2, k1=v1}")));
  }

  public void testSerial() {
    assertTrue ( _nbhm.isEmpty() );
    assertThat ( _nbhm.put("k1","v1"), nullValue() );
    assertThat ( _nbhm.put("k2","v2"), nullValue() );

    // Serialize it out
    try {
      FileOutputStream fos = new FileOutputStream("NBHM_test.txt");
      ObjectOutputStream out = new ObjectOutputStream(fos);
      out.writeObject(_nbhm);
      out.close();
    } catch(IOException ex) {
      ex.printStackTrace();
    }

    // Read it back
    try {
      File f = new File("NBHM_test.txt");
      FileInputStream fis = new FileInputStream(f);
      ObjectInputStream in = new ObjectInputStream(fis);
      NonBlockingHashMap nbhm = (NonBlockingHashMap)in.readObject();
      in.close();
      assertThat("serialization works",nbhm.toString(), anyOf(is("{k1=v1, k2=v2}"),is("{k2=v2, k1=v1}")));
      if( !f.delete() ) throw new IOException("delete failed");
    } catch(IOException ex) {
      ex.printStackTrace();
    } catch(ClassNotFoundException ex) {
      ex.printStackTrace();
    }
  }

  public void testIterationBig2() {
    final int CNT = 10000;
    NonBlockingHashMap<Integer,String> nbhm = new NonBlockingHashMap<Integer,String>();
    final String v = "v";
    for( int i=0; i<CNT; i++ ) {
      final Integer z = new Integer(i);
      String s0 = nbhm.get(z);
      assertThat( s0, nullValue() );
      nbhm.put(z,v);
      String s1 = nbhm.get(z);
      assertThat( s1, is(v) );
    }
    assertThat( nbhm.size(), is(CNT) ); 
 }

  public void testIterationBig() {
    final int CNT = 10000;
    assertThat( _nbhm.size(), is(0) );
    for( int i=0; i<CNT; i++ )
      _nbhm.put("k"+i,"v"+i);
    assertThat( _nbhm.size(), is(CNT) );

    int sz =0;
    int sum = 0;
    for( String s : _nbhm.keySet() ) {
      sz++;
      assertThat("",s.charAt(0),is('k'));
      int x = Integer.parseInt(s.substring(1));
      sum += x;
      assertTrue(x>=0 && x<=(CNT-1));
    }
    assertThat("Found 10000 ints",sz,is(CNT));
    assertThat("Found all integers in list",sum,is(CNT*(CNT-1)/2));

    assertThat( "can remove 3", _nbhm.remove("k3"), is("v3") );
    assertThat( "can remove 4", _nbhm.remove("k4"), is("v4") );
    sz =0;
    sum = 0;
    for( String s : _nbhm.keySet() ) {
      sz++;
      assertThat("",s.charAt(0),is('k'));
      int x = Integer.parseInt(s.substring(1));
      sum += x;
      assertTrue(x>=0 && x<=(CNT-1));
      String v = _nbhm.get(s);
      assertThat("",v.charAt(0),is('v'));
      assertThat("",s.substring(1),is(v.substring(1)));
    }
    assertThat("Found "+(CNT-2)+" ints",sz,is(CNT-2));
    assertThat("Found all integers in list",sum,is(CNT*(CNT-1)/2 - (3+4)));
  }

  // Do some simple concurrent testing
  public void testConcurrentSimple() throws InterruptedException {
    final NonBlockingHashMap<String,String> nbhm = new NonBlockingHashMap<String,String>();

    // In 2 threads, add & remove even & odd elements concurrently
    Thread t1 = new Thread() { public void run() { work_helper(nbhm,"T1",1); } };
    t1.start();
    work_helper(nbhm,"T0",0);
    t1.join();

    // In the end, all members should be removed
    StringBuffer buf = new StringBuffer();
    buf.append("Should be emptyset but has these elements: {");
    boolean found = false;
    for( String x : nbhm.keySet() ) {
      buf.append(" ").append(x);
      found = true;
    }
    if( found ) System.out.println(buf+" }");
    assertThat( "concurrent size=0", nbhm.size(), is(0) );
    for( String x : nbhm.keySet() ) {
      assertTrue("No elements so never get here",false);
    }
  }

  void work_helper(NonBlockingHashMap<String,String> nbhm, String thrd, int d) {
    final int ITERS = 20000;
    for( int j=0; j<10; j++ ) {
      long start = System.nanoTime();
      for( int i=d; i<ITERS; i+=2 )
        assertThat( "this key not in there, so putIfAbsent must work",
                    nbhm.putIfAbsent("k"+i,thrd), is((String)null) );
      for( int i=d; i<ITERS; i+=2 )
        assertTrue( nbhm.remove("k"+i,thrd) );
      double delta_nanos = System.nanoTime()-start;
      double delta_secs = delta_nanos/1000000000.0;
      double ops = ITERS*2;
      //System.out.println("Thrd"+thrd+" "+(ops/delta_secs)+" ops/sec size="+nbhm.size());
    }
  }

  public final void testNonBlockingHashMapSize() {
    NonBlockingHashMap<Long,String> items = new NonBlockingHashMap<Long,String>();
    items.put(Long.valueOf(100), "100");
    items.put(Long.valueOf(101), "101");

    assertEquals("keySet().size()", 2, items.keySet().size());
    assertTrue("keySet().contains(100)", items.keySet().contains(Long.valueOf(100)));
    assertTrue("keySet().contains(101)", items.keySet().contains(Long.valueOf(101)));

    assertEquals("values().size()", 2, items.values().size());
    assertTrue("values().contains(\"100\")", items.values().contains("100"));
    assertTrue("values().contains(\"101\")", items.values().contains("101"));

    assertEquals("entrySet().size()", 2, items.entrySet().size());
    boolean found100 = false;
    boolean found101 = false;
    for (Entry<Long, String> entry : items.entrySet()) {
      if (entry.getKey().equals(Long.valueOf(100))) {
        assertEquals("entry[100].getValue()==\"100\"", "100", entry.getValue());
        found100 = true;
      } else if (entry.getKey().equals(Long.valueOf(101))) {
        assertEquals("entry[101].getValue()==\"101\"", "101", entry.getValue());
        found101 = true;
      }
    }
    assertTrue("entrySet().contains([100])", found100);
    assertTrue("entrySet().contains([101])", found101);
  }

  // Concurrent insertion & then iterator test.
  static public void testNonBlockingHashMapIterator() throws InterruptedException {
    final int ITEM_COUNT1 = 1000;
    final int THREAD_COUNT = 5;
    final int PER_CNT = ITEM_COUNT1/THREAD_COUNT;
    final int ITEM_COUNT = PER_CNT*THREAD_COUNT; // fix roundoff for odd thread counts

    NonBlockingHashMap<Long,TestKey> nbhml = new NonBlockingHashMap<Long,TestKey>();
    // use a barrier to open the gate for all threads at once to avoid rolling
    // start and no actual concurrency
    final CyclicBarrier barrier = new CyclicBarrier(THREAD_COUNT);
    final ExecutorService ex = Executors.newFixedThreadPool(THREAD_COUNT);
    final CompletionService<Object> co = new ExecutorCompletionService<Object>(ex);
    for( int i=0; i<THREAD_COUNT; i++ ) {
      co.submit(new NBHMLFeeder(nbhml, PER_CNT, barrier, i*PER_CNT));
    }
    for( int retCount = 0; retCount < THREAD_COUNT; retCount++ ) {
      co.take();
    }
    ex.shutdown();

    assertEquals("values().size()", ITEM_COUNT, nbhml.values().size());
    assertEquals("entrySet().size()", ITEM_COUNT, nbhml.entrySet().size());
    int itemCount = 0;
    for( TestKey K : nbhml.values() )
      itemCount++;
    assertEquals("values().iterator() count", ITEM_COUNT, itemCount);
  }

  // --- NBHMLFeeder ---
  // Class to be called from another thread, to get concurrent installs into
  // the table.
  static private class NBHMLFeeder implements Callable<Object> {
    static private final Random _rand = new Random(System.currentTimeMillis());
    private final NonBlockingHashMap<Long,TestKey> _map;
    private final int _count;
    private final CyclicBarrier _barrier;
    private final long _offset;
    public NBHMLFeeder(final NonBlockingHashMap<Long,TestKey> map, final int count, final CyclicBarrier barrier, final long offset) {
      _map = map;
      _count = count;
      _barrier = barrier;
      _offset = offset;
    }
    public Object call() throws Exception {
      _barrier.await();         // barrier, to force racing start
      for( long j=0; j<_count; j++ )
        _map.put(j+_offset, new TestKey(_rand.nextLong(),_rand.nextInt (), (short) _rand.nextInt(Short.MAX_VALUE)));
      return null;
    }
  }

  // --- TestKey ---
  // Funny key tests all sorts of things, has a pre-wired hashCode & equals.
  static private final class TestKey {
    public final int  _type;
    public final long _id;
    public final int  _hash;
    public TestKey(final long id, final int type, int hash) {
      _id = id;
      _type = type;
      _hash = hash;
    }
    public int hashCode() { return _hash;  }
    public boolean equals(Object object) {
      if (null == object) return false;
      if (object == this) return true;
      if (object.getClass() != this.getClass()) return false;
      final TestKey other = (TestKey) object;
      return (this._type == other._type && this._id == other._id);
    }
    public String toString() { return String.format("%s:%d,%d,%d", getClass().getSimpleName(), _id, _type, _hash);  }
  }

  // --- Customer Test Case 3 ------------------------------------------------
  private TestKeyFeeder getTestKeyFeeder() {
    final TestKeyFeeder feeder = new TestKeyFeeder();
    feeder.checkedPut(10401000001844L, 657829272, 680293140); // section 12
    feeder.checkedPut(10401000000614L, 657829272, 401326994); // section 12
    feeder.checkedPut(10400345749304L, 2095121916, -9852212); // section 12
    feeder.checkedPut(10401000002204L, 657829272, 14438460); // section 12
    feeder.checkedPut(10400345749234L, 1186831289, -894006017); // section 12
    feeder.checkedPut(10401000500234L, 969314784, -2112018706); // section 12
    feeder.checkedPut(10401000000284L, 657829272, 521425852); // section 12
    feeder.checkedPut(10401000002134L, 657829272, 208406306); // section 12
    feeder.checkedPut(10400345749254L, 2095121916, -341939818); // section 12
    feeder.checkedPut(10401000500384L, 969314784, -2136811544); // section 12
    feeder.checkedPut(10401000001944L, 657829272, 935194952); // section 12
    feeder.checkedPut(10400345749224L, 1186831289, -828214183); // section 12
    feeder.checkedPut(10400345749244L, 2095121916, -351234120); // section 12
    feeder.checkedPut(10400333128994L, 2095121916, -496909430); // section 12
    feeder.checkedPut(10400333197934L, 2095121916, 2147144926); // section 12
    feeder.checkedPut(10400333197944L, 2095121916, -2082366964); // section 12
    feeder.checkedPut(10400336947684L, 2095121916, -1404212288); // section 12
    feeder.checkedPut(10401000000594L, 657829272, 124369790); // section 12
    feeder.checkedPut(10400331896264L, 2095121916, -1028383492); // section 12
    feeder.checkedPut(10400332415044L, 2095121916, 1629436704); // section 12
    feeder.checkedPut(10400345749614L, 1186831289, 1027996827); // section 12
    feeder.checkedPut(10401000500424L, 969314784, -1871616544); // section 12
    feeder.checkedPut(10400336947694L, 2095121916, -1468802722); // section 12
    feeder.checkedPut(10410002672481L, 2154973, 1515288586); // section 12
    feeder.checkedPut(10410345749171L, 2154973, 2084791828); // section 12
    feeder.checkedPut(10400004960671L, 2154973, 1554754674); // section 12
    feeder.checkedPut(10410009983601L, 2154973, -2049707334); // section 12
    feeder.checkedPut(10410335811601L, 2154973, 1547385114); // section 12
    feeder.checkedPut(10410000005951L, 2154973, -1136117016); // section 12
    feeder.checkedPut(10400004938331L, 2154973, -1361373018); // section 12
    feeder.checkedPut(10410001490421L, 2154973, -818792874); // section 12
    feeder.checkedPut(10400001187131L, 2154973, 649763142); // section 12
    feeder.checkedPut(10410000409071L, 2154973, -614460616); // section 12
    feeder.checkedPut(10410333717391L, 2154973, 1343531416); // section 12
    feeder.checkedPut(10410336680071L, 2154973, -914544144); // section 12
    feeder.checkedPut(10410002068511L, 2154973, -746995576); // section 12
    feeder.checkedPut(10410336207851L, 2154973, 863146156); // section 12
    feeder.checkedPut(10410002365251L, 2154973, 542724164); // section 12
    feeder.checkedPut(10400335812581L, 2154973, 2146284796); // section 12
    feeder.checkedPut(10410337345361L, 2154973, -384625318); // section 12
    feeder.checkedPut(10410000409091L, 2154973, -528258556); // section 12
    return feeder;
  }

  // ---
  static private class TestKeyFeeder {
    private final Hashtable<Integer, List<TestKey>> _items = new Hashtable<Integer, List<TestKey>>();
    private int _size = 0;
    public int size() { return _size;  }
    // Put items into the hashtable, sorted by 'type' into LinkedLists.
    public void checkedPut(final long id, final int type, final int hash) {
      _size++;
      final TestKey item = new TestKey(id, type, hash);
      if( !_items.containsKey(type) )
        _items.put(type, new LinkedList<TestKey>());
      _items.get(type).add(item);
    }

    public NonBlockingHashMap<Long,TestKey> getMapMultithreaded() throws InterruptedException, ExecutionException {
      final int threadCount = _items.keySet().size();
      final NonBlockingHashMap<Long,TestKey> map = new NonBlockingHashMap<Long,TestKey>();

      // use a barrier to open the gate for all threads at once to avoid rolling start and no actual concurrency
      final CyclicBarrier barrier = new CyclicBarrier(threadCount);
      final ExecutorService ex = Executors.newFixedThreadPool(threadCount);
      final CompletionService<Integer> co = new ExecutorCompletionService<Integer>(ex);
      for( Integer type : _items.keySet() ) {
        // A linked-list of things to insert
        List<TestKey> items = _items.get(type);
        TestKeyFeederThread feeder = new TestKeyFeederThread(type, items, map, barrier);
        co.submit(feeder);
      }

      // wait for all threads to return
      int itemCount = 0;
      for( int retCount = 0; retCount < threadCount; retCount++ ) {
        final Future<Integer> result = co.take();
        itemCount += result.get();
      }
      ex.shutdown();
      return map;
    }
  }

  // --- TestKeyFeederThread
  static private class TestKeyFeederThread implements Callable<Integer> {
    private final int _type;
    private final NonBlockingHashMap<Long,TestKey> _map;
    private final List<TestKey> _items;
    private final CyclicBarrier _barrier;
    public TestKeyFeederThread(final int type, final List<TestKey> items, final NonBlockingHashMap<Long,TestKey> map, final CyclicBarrier barrier) {
      _type = type;
      _map = map;
      _items = items;
      _barrier = barrier;
    }

    public Integer call() throws Exception {
      _barrier.await();
      int count = 0;
      for( TestKey item : _items ) {
        if (_map.contains(item._id)) {
          System.err.printf("COLLISION DETECTED: %s exists\n", item.toString());
        }
        final TestKey exists = _map.putIfAbsent(item._id, item);
        if (exists == null) {
          count++;
        } else {
          System.err.printf("COLLISION DETECTED: %s exists as %s\n", item.toString(), exists.toString());
        }
      }
      return count;
    }
  }

  // ---
  public void testNonBlockingHashMapIteratorMultithreaded() throws InterruptedException, ExecutionException {
    TestKeyFeeder feeder = getTestKeyFeeder();
    final int itemCount = feeder.size();

    // validate results
    final NonBlockingHashMap<Long,TestKey> items = feeder.getMapMultithreaded();
    assertEquals("size()", itemCount, items.size());

    assertEquals("values().size()", itemCount, items.values().size());

    assertEquals("entrySet().size()", itemCount, items.entrySet().size());

    int iteratorCount = 0;
    for( TestKey m : items.values() )
      iteratorCount++;
    // sometimes a different result comes back the second time
    int iteratorCount2 = 0;
    for( Iterator<TestKey> it = items.values().iterator(); it.hasNext(); ) {
      iteratorCount2++;
      it.next();
    }
    assertEquals("iterator counts differ", iteratorCount, iteratorCount2);
    assertEquals("values().iterator() count", itemCount, iteratorCount);
  }

  // --- Customer Test Case 4 ------------------------------------------------
  static private class Test4 implements Runnable {
    //
    // Constants
    //
    static private final int THREADS = 4;
    static private final int KEYS = 4;
    static volatile private AssertionError AE;
    //
    // Instance Fields
    //
    private final ConcurrentMap<Integer,Integer> _map;
    
    //
    // Constructor
    //
    private Test4(ConcurrentMap<Integer,Integer> map) { _map = map;  }

    public void run() {
      try {
        // Run long enough to trigger compilations, etc
        for( int i=0; i<100000 && AE==null; i++ ) 
          pass();
      } catch( AssertionError e ) {
        AE = e;
      }
    }

    private void pass() {
      for( int i = 0    ; i < KEYS; i++)  increment(i);
      for( int i = KEYS -1; i >= 0; i--)  decrement(i);
    }

    // increment count entry for given value, creating it of necessary
    private void increment(int i) {
      //Integer key = new Integer(i);
      Integer key = Integer.valueOf(i);
      while( AE==null ) {
        // first assume no old count
        //Integer oldValue = _map.putIfAbsent(key, new Integer(1));
        Integer oldValue = _map.putIfAbsent(key, Integer.valueOf(1));
        if (oldValue == null) // done, was no old entry and have made new entry with value 1
          return;
        
        int newCount = oldValue.intValue() + 1;
        if (newCount > THREADS) 
          // shouldn't happen since can only be incremented at most once
          // by each thread before corresponding decrement
          throw new AssertionError("count for " + i + " incremented to " + newCount);
        
        //Integer newValue = new Integer(newCount);
        Integer newValue = Integer.valueOf(newCount);
        if (_map.replace(key, oldValue, newValue)) // done, have atomically incremented value by 1
          return;
      } // lost update race, retry
    }
    
    // decrement count entry for given value, removing it if goes to zero
    private void decrement(int i) {
      //Integer key = new Integer(i);
      Integer key = Integer.valueOf(i);
      while( AE==null ) {
        Integer oldValue = _map.get(key);
        if (oldValue == null) 
          // should not happen because of previous increment for same key
          // but does - THIS IS THE BUG
          throw new AssertionError("BUG REPRODUCED, MISSING COUNT FOR " + i );
        
        int newCount = oldValue.intValue() - 1;
        if (newCount < 0 ) // shouldn't happen since each decrement preceded by a corresponding increment
          throw new AssertionError("count for " + i + " decremented to " + newCount);
        
        if (newCount == 0) {
          if (_map.remove(key, oldValue)) // done have atomically removed final 1 count
            return;
        } else {
          //Integer newValue = new Integer(newCount);
          Integer newValue = Integer.valueOf(newCount);
          if (_map.replace(key, oldValue, newValue)) // done have atomically replace with count 1 lower
            return;
        }
      } // lost update race, retry
    }
  }

  // --- testConcurrentRemove
  public void testConcurrentRemove() throws InterruptedException {
    ConcurrentMap<Integer,Integer> map
      // = new ConcurrentHashMap<Integer,Integer>();
      = new NonBlockingHashMap<Integer,Integer>();
    map.put   (Integer.valueOf(0),Integer.valueOf(1));
    map.remove(Integer.valueOf(0));
    
    Test4.AE = null;
    Thread[] ts = new Thread[Test4.THREADS];
    for( int i = 0; i < Test4.THREADS; i++ )
      (ts[i] = new Thread(new Test4(map), "thread " + i)).start();
    // See if we handed due to a crash/bug
    for( int i = 0; i < Test4.THREADS; i++ )
      ts[i].join();
    assertTrue( Test4.AE==null );
  }

  // This test is a copy of the JCK test Hashtable2027, which is incorrect.
  // The test requires a particular order of values to appear in the esa
  // array - but this is not part of the spec.  A different implementation
  // might put the same values into the array but in a different order.
  //public void testToArray() {
  //  NonBlockingHashMap ht = new NonBlockingHashMap();
  //
  //  ht.put("Nine", new Integer(9));
  //  ht.put("Ten", new Integer(10));
  //  ht.put("Ten1", new Integer(100));
  //
  //  Collection es = ht.values();
  //
  //  Object [] esa = es.toArray();
  //
  //  ht.remove("Ten1");
  //
  //  assertEquals( "size check", es.size(), 2 );
  //  assertEquals( "iterator_order[0]", new Integer( 9), esa[0] );
  //  assertEquals( "iterator_order[1]", new Integer(10), esa[1] );
  //}
}
