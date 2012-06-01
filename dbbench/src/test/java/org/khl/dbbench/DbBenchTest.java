package org.khl.dbbench;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.BitSet;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.carrotsearch.randomizedtesting.RandomizedTest;
import com.carrotsearch.randomizedtesting.annotations.Repeat;


public class DbBenchTest extends RandomizedTest {

    private static final long prime2 = 557443;
    private static final long prime1 = 1299709;
    private static Connection conn;
    private static int batch ; 
    private static int manyRows ;
    

    @BeforeClass
    public static void fill() throws SQLException, ClassNotFoundException{
        batch = isNightly() ? 1000 : 100;
        manyRows = batch*(isNightly() ? 1000 : 100);
        
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        conn = DriverManager.getConnection("jdbc:derby:testdb;create=true");
        
        final Statement stm = conn.createStatement();
        
        try{
            assertFalse(stm.execute("CREATE TABLE FIRSTTABLE (ID INT PRIMARY KEY, NAME VARCHAR(120))"));
        }catch(Exception e){
            System.out.println("ignoring "+ e);
        }
        
        if(howManyRowsAlreadyThere(stm)==manyRows){
            System.out.println("skips data insertion");
            return;
        }
        
        assertFalse(stm.execute("DELETE FROM FIRSTTABLE"));
        stm.close(); 
        
        conn.setAutoCommit(false);
        final PreparedStatement insStam = conn.prepareStatement("INSERT INTO FIRSTTABLE VALUES (?,?)");
        int updCount = 0;
        // one mln I wanna to put
        for(int i=0; i<manyRows;){
            long id = nthId(i++); 
            //System.out.println(""+(i-1) +" "+id);
            insStam.setInt(1, (int)id);
            insStam.setString(2, id+"th");
            insStam.addBatch();
            if(i%batch==0 && i>0){
                final int[] cntz;
                cntz = insStam.executeBatch();
                for(int c :cntz)
                    updCount += c;
            }
        }
        insStam.close();
        conn.commit();
        
        assertEquals(manyRows, updCount);
        conn.setAutoCommit(true);
    }

    private static int howManyRowsAlreadyThere(final Statement stm)
            throws SQLException {
        final ResultSet curNumRowz = stm.executeQuery("select count(*) from FIRSTTABLE");
        assertTrue(curNumRowz.next());
        final int actRowz = curNumRowz.getInt(1);
        curNumRowz.close();
        return actRowz;
    }

    private static int nthId(int i) {
        return (int) ((i*prime1+prime2)%manyRows);
    }
    
    @Test
    @Repeat(iterations = 10)
    public void pullOneByOne() throws SQLException {
        final Statement stm = conn.createStatement();
        final ResultSet cursor = stm.executeQuery("select * from FIRSTTABLE order by ID desc");
        final BitSet set = new BitSet(manyRows);
        while(cursor.next()){
            final int id = cursor.getInt(1);
            assertFalse(cursor.wasNull());
            assertEquals(id+"th", cursor.getString(2));
            assertFalse(set.get(id));
            set.set(id);
        }
        assertEquals(manyRows, set.cardinality());
        cursor.close();
        stm.close();
    }
    
    @Test
     @Repeat(iterations = 10)
     public void pullByBatches() throws SQLException {
        
         final StringBuilder sb = new StringBuilder("select * from FIRSTTABLE where id in (");
         for(int i=0;i<batch;i++){
             sb.append("?,");
         }
         sb.setCharAt(sb.length()-1, ')');
         
        final PreparedStatement stm = conn.prepareStatement(sb.toString());
        final BitSet set = new BitSet(manyRows);
         for(int i=manyRows-1; i>=0;i--){
             stm.setInt(batch-i%batch, nthId(i));
             if(i%batch==0){
                 final ResultSet cursor = stm.executeQuery();
                 while(cursor.next()){
                     final int id = cursor.getInt(1);
                     assertFalse(cursor.wasNull());
                     assertEquals(id+"th", cursor.getString(2));
                     assertFalse(set.get(id));
                     set.set(id);
                 }
                 cursor.close();
             }
         }
         
         assertEquals(manyRows, set.cardinality());
         stm.close();
     }
    
    @AfterClass
    public static void stop() throws SQLException{
        
        try {
            DriverManager.getConnection("jdbc:derby:;shutdown=true");
        } catch (Exception e) {
            System.out.println("ignoring "+e);
        }
    }
}
