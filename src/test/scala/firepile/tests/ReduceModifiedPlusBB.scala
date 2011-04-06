package firepile.tests

object ReduceModifiedPlusBB {

  import firepile._
  import firepile.Device
  import firepile.Space
  import firepile.Arrays._
  import firepile.Kernel._
  import firepile.util.BufferBackedArray._
  import firepile.tree.Trees.Tree
  import com.nativelibs4java.opencl._
  import com.nativelibs4java.util._
  import java.nio.FloatBuffer
  import java.nio.ByteOrder
  import scala.collection.JavaConversions._
  import firepile.Marshaling._
  import scala.util.Random
 


  class size(exp: Int) extends scala.StaticAnnotation { }
  class local extends scala.StaticAnnotation { }

  
  var NUM_ITEMS = 16384 // 1048576
 
  def main(args: Array[String]) = {

  // if (args.length > 0) NUM_ITEMS= if (args.length > 0) (1 << args(0).toInt) else ( 1 << 20)
  run
  	
  }

  def run = {
    val random = new Random(0)
    val randInput = Array.fill(NUM_ITEMS)(random.nextFloat)
    val cpuSum = randInput.sum
    val gpuSum = reduceModified(BBArray.fromArray(randInput))(firepile.gpu)

    println("CPU sum = " + cpuSum + "   GPU sum = " + gpuSum)

  }

// def reduceModified(idata: Array[Float], f: (Float,Float) => Float)
def reduceModified(idata: BBArray[Float])
 (implicit dev: Device): Float = {
 
  
  val space=dev.defaultPaddedPartition(idata.length)
  //dev.setWorkSizes(NUM_ITEMS, space.blocks)
 val odata = BBArray.ofDim[Float](space.blocks)
 // val odata = new Array[Float](64)
 println("Block size = " + space.blocks)
 val n = idata.length
 Kernel.output("odata")
 
 space.spawn { 
   
  space.groups.foreach {
    g => {
         
        val sdata = Array.ofDim[Float](g.items.size)  
   
        g.items.foreach {
          item => { 
           
            val i = g.id * (g.items.size * 2) + item.id  
           
            if (i < n) sdata(item.id) = idata(i) 
            else sdata(item.id) = 0f
 
            if (i + g.items.size < n)
              sdata(item.id) += idata(i + g.items.size)
 
            g.barrier
 
            var k = g.items.size / 2
            
            while ( k > 0 ) {
              if (item.id < k)
	        sdata(item.id) += sdata(item.id + k)
               g.barrier
              k>>=1
            }

          
            if (item.id == 0) 
              odata(g.id) =  sdata(0)
          }
        }
      }
    }
     // odata escapes -- so need to pass in to the generated kernel 
     (odata,idata,n)
     
 }
   
   odata.reduceLeft(_+_)
  }
  
}
