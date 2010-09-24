import firepile.util.BufferBackedArray._

import java.nio.ByteBuffer
import java.nio.ByteOrder

import scala.reflect.Manifest

import com.nativelibs4java.opencl.CLByteBuffer
import com.nativelibs4java.opencl.CLEvent
import com.nativelibs4java.opencl.CLKernel
import com.nativelibs4java.opencl.CLKernel.LocalSize
import com.nativelibs4java.opencl.JavaCL

package object firepile {
  import Wrappers._
  import Spaces._
  import Marshaling._
  import Args._

  private lazy val defaultGPUPlatform: Option[Platform] = {
    platforms.flatMap {
      (p:Platform) => p.listGPUs(true).map { (d:Device) => p }
    }.headOption
  }

  private lazy val defaultCPUPlatform: Option[Platform] = {
    platforms.flatMap {
      (p:Platform) => p.listCPUs(true).map { (d:Device) => p }
    }.headOption
  }

  private lazy val defaultGPU: Option[Device] = {
    defaultGPUPlatform match {
      case Some(p) => p.listGPUs(true).headOption
      case None => None
    }
  }

  private lazy val defaultCPU: Option[Device] = {
    defaultCPUPlatform match {
      case Some(p) => p.listCPUs(true).headOption
      case None => None
    }
  }

  private lazy val bestDevice: Device = platforms(0).bestDevice

  lazy val gpu = defaultGPU match {
    case Some(d) => d
    case None => null
  }

  implicit def BBArrayMarshal[A: FixedSizeMarshal]: BBArrayMarshal[A] = new BBArrayMarshal[A]

  def finish[A](dev: Device)(body: => A): A = {
    try {
      body
    }
    finally {
      dev.queue.finish
    }
  }

  implicit def bi12bb[A: FixedSizeMarshal](a: GroupIndexed1[A]): BBArray[A] = a.backing
  implicit def bi22bb[A: FixedSizeMarshal](a: GroupIndexed2[A]): BBArray[A] = a.backing
  implicit def bi32bb[A: FixedSizeMarshal](a: GroupIndexed3[A]): BBArray[A] = a.backing

  implicit def ti12bb[A: FixedSizeMarshal](a: ThreadIndexed1[A]): BBArray[A] = a.backing
  implicit def ti22bb[A: FixedSizeMarshal](a: ThreadIndexed2[A]): BBArray[A] = a.backing
  implicit def ti32bb[A: FixedSizeMarshal](a: ThreadIndexed3[A]): BBArray[A] = a.backing

  implicit def a2bb[A: FixedSizeMarshal](a: Array[A]): BBArray[A] = BBArray.fromArray[A](a).directCopy
  implicit def bb2a[A](b: BBArray[A])(implicit aMarshal: FixedSizeMarshal[A], Ma: ClassManifest[A]): Array[A] = Array.tabulate[A](b.length)(i => b(i))

  implicit def wrapArray1a[A: FixedSizeMarshal](a: Array[A]) = new BBArrayKernelWrapper[A](a)
  implicit def wrapArray1b[A: FixedSizeMarshal](a: BBArray[A]) = new BBArrayKernelWrapper[A](a)

  implicit def wrapArray2aa[A1: FixedSizeMarshal,A2: FixedSizeMarshal](p: (Array[A1],Array[A2])) = new BBSpawner2[A1,A2](p._1, p._2)
  implicit def wrapArray2ab[A1: FixedSizeMarshal,A2: FixedSizeMarshal](p: (Array[A1],BBArray[A2])) = new BBSpawner2[A1,A2](p._1, p._2)
  implicit def wrapArray2ba[A1: FixedSizeMarshal,A2: FixedSizeMarshal](p: (BBArray[A1],Array[A2])) = new BBSpawner2[A1,A2](p._1, p._2)
  implicit def wrapArray2bb[A1: FixedSizeMarshal,A2: FixedSizeMarshal](p: (BBArray[A1],BBArray[A2])) = new BBSpawner2[A1,A2](p._1, p._2)
  implicit def wrapArray3aaa[A1: FixedSizeMarshal,A2: FixedSizeMarshal,A3: FixedSizeMarshal](p: (Array[A1],Array[A2],Array[A3])) = new BBSpawner3[A1,A2,A3](p._1, p._2, p._3)
  implicit def wrapArray3aab[A1: FixedSizeMarshal,A2: FixedSizeMarshal,A3: FixedSizeMarshal](p: (Array[A1],Array[A2],BBArray[A3])) = new BBSpawner3[A1,A2,A3](p._1, p._2, p._3)
  implicit def wrapArray3aba[A1: FixedSizeMarshal,A2: FixedSizeMarshal,A3: FixedSizeMarshal](p: (Array[A1],BBArray[A2],Array[A3])) = new BBSpawner3[A1,A2,A3](p._1, p._2, p._3)
  implicit def wrapArray3abb[A1: FixedSizeMarshal,A2: FixedSizeMarshal,A3: FixedSizeMarshal](p: (Array[A1],BBArray[A2],BBArray[A3])) = new BBSpawner3[A1,A2,A3](p._1, p._2, p._3)
  implicit def wrapArray3baa[A1: FixedSizeMarshal,A2: FixedSizeMarshal,A3: FixedSizeMarshal](p: (BBArray[A1],Array[A2],Array[A3])) = new BBSpawner3[A1,A2,A3](p._1, p._2, p._3)
  implicit def wrapArray3bab[A1: FixedSizeMarshal,A2: FixedSizeMarshal,A3: FixedSizeMarshal](p: (BBArray[A1],Array[A2],BBArray[A3])) = new BBSpawner3[A1,A2,A3](p._1, p._2, p._3)
  implicit def wrapArray3bba[A1: FixedSizeMarshal,A2: FixedSizeMarshal,A3: FixedSizeMarshal](p: (BBArray[A1],BBArray[A2],Array[A3])) = new BBSpawner3[A1,A2,A3](p._1, p._2, p._3)
  implicit def wrapArray3bbb[A1: FixedSizeMarshal,A2: FixedSizeMarshal,A3: FixedSizeMarshal](p: (BBArray[A1],BBArray[A2],BBArray[A3])) = new BBSpawner3[A1,A2,A3](p._1, p._2, p._3)

  def mapKernel[A,B](k: BBArrayMapKernel1[A,B])(a: BBArray[A]) = k(a)
  def mapKernel[A1,A2,B](k: BBArrayMapKernel2[A1,A2,B])(a1: BBArray[A1], a2: BBArray[A2]) = k(a1,a2)
  def mapKernel[A1,A2,A3,B](k: BBArrayMapKernel3[A1,A2,A3,B])(a1: BBArray[A1], a2: BBArray[A2], a3: BBArray[A3]) = k(a1,a2,a3)
  def reduceKernel[A](k: BBArrayReduceKernel1[A])(a: BBArray[A]) = k(a)

  def spawn[A,B](k: BBArrayLocalReduceKernel1[A,B])(a: BBArray[A]) = k(a)

  def spawn[B](k: Future[B]) = k.start
  /*
  def spawn[B](k: Future[B])(implicit dev: Device) = k.start(dev)
  def spawn[B](dev: Device)(k: Future[B]) = k.start(dev)
  */

  // How to make kernels composable
  // Basic kernels:
  // foreach : (A => Unit) => List[A] => Unit
  // map     : (A => B) => List[A] => List[B]
  // flatMap : (A => Iterable[B]) => List[A] => List[B]
  // filter  : (A => Boolean) => List[A] => List[A]

  // zip     : (List[A], List[B]) => List[(A,B)]
  // unzip   : List[(A,B)] => (List[A], List[B])

  // zipWith : ((A,B) => C) => (List[A], List[B]) => List[C]

  // *K -- kernels, run on GPU
  // *F -- functions, run on host

  // MapK    : (A => B) => Global[A] => Global[B]
  // BlockReduceK : ((A,A) => A) => Global[A] => Block[A]
  // ReduceF : ((A,A) => A) => Block[A] => A
  // ReduceK : ((A,A) => A) => Global[A] => A

  // BlockZipReduceK : ((A,A,A) => A) => Global[A] => Block[A]

  // mkDotProduct = BlockZipReduceK o ReduceF


  // mkReduceK : ((A,A) => A) => ReduceK = (mkBlockReduceK(f) o mkReduceF(f))

  // Should copies to/from device be explicit?

  def time[A](body: => A): A = {
    val t0 = System.currentTimeMillis
    try {
      body
    }
    finally {
      val t1 = System.currentTimeMillis
      println("time " + (t1 - t0) / 1000.)
    }
  }

  implicit def f2bbarrayMapk1[A,B](f: A => B)(implicit baMarshal: FixedSizeMarshal[A], bMarshal: FixedSizeMarshal[B], dev: Device): BBArrayMapKernel1[A,B] =
        Compiler.f2bbarrayMapk1[A,B](f)(baMarshal, bMarshal, dev)

  implicit def f2bbarrayLocalReducek1[A,B,L](f: BBArray[A] => GroupIndexed1[B] => (Id1, LocalIndexed1[L]) => Unit)(implicit localMarshal: FixedSizeMarshal[L], aaMarshal: FixedSizeMarshal[A], bMarshal: FixedSizeMarshal[B], dev: Device): BBArrayLocalReduceKernel1[A,B] =
        Compiler.f2bbarrayLocalReducek1[A,B,L](f)(localMarshal, aaMarshal, bMarshal, dev)

  implicit def f2bbarrayMapk2[A1,A2,B](f: (A1,A2) => B)(implicit aMarshal1: FixedSizeMarshal[A1], aMarshal2: FixedSizeMarshal[A2], bMarshal: FixedSizeMarshal[B], dev: Device): BBArrayMapKernel2[A1,A2,B] =
        Compiler.f2bbarrayMapk2[A1,A2,B](f)(aMarshal1, aMarshal2, bMarshal, dev)


  implicit def f2bbarrayReducek1[A](f: (A,A) => A)(implicit caMarshal: FixedSizeMarshal[A], dev: Device): BBArrayReduceKernel1[A] =
        Compiler.f2bbarrayReducek1[A](f)(caMarshal, dev)

  def platforms: List[Platform] = {
    JavaCL.listPlatforms.map(p => new Platform(p)).toList
  }

  def listGPUPoweredPlatforms: List[Platform] = {
    JavaCL.listGPUPoweredPlatforms.map(p => new Platform(p)).toList
  }

  // ------------------------------------------------------------------------
  def unloadCompiler = JavaCL.unloadCompiler

  // ------------------------------------------------------------------------
  trait Dist {
    def totalNumberOfItems: Int
    def numberOfItemsPerGroup: Int = 1
    override def toString = "Dist {n=" + totalNumberOfItems + "/" + numberOfItemsPerGroup + "}"
  }

  trait HasLength[A] {
    def length(a: A): Int
  }

  type Dist1[A] = Function1[A,Dist]
  type Dist2[A1,A2] = Function2[A1,A2,Dist]
  type Dist3[A1,A2,A3] = Function3[A1,A2,A3,Dist]

  class SimpleArrayDist1[T: HasLength] extends Dist1[T] {
    def apply(a: T) = new Dist {
      val totalNumberOfItems = implicitly[HasLength[T]].length(a)
      /*
      lazy val totalNumberOfItems = {
        val len = implicitly[HasLength[T]].length(a)
        val numThreads = numberOfItemsPerGroup
        (len + numThreads - 1) / numThreads * numThreads
      }
      override lazy val numberOfItemsPerGroup: Int = {
        val len = implicitly[HasLength[T]].length(a)
        var numThreads = len / 16
        var i = 1
        while (numThreads > i)
          i = i * 2
        numThreads = i
        if (numThreads > 256)
          numThreads = 256
        numThreads
      }
      */
    }
  }

  class SimpleArrayDist2[T: HasLength, U: HasLength] extends Dist2[T,U] {
    def apply(a1: T, a2: U) = new Dist {
      val totalNumberOfItems = implicitly[HasLength[T]].length(a1) max implicitly[HasLength[U]].length(a2)
    }
  }

  class BlockArrayDist1[T: HasLength](n: Int = 32) extends Dist1[T] {
    def apply(a: T) = new Dist {
      // Round up to next block size
      val totalNumberOfItems = (implicitly[HasLength[T]].length(a) + n - 1) / n * n
      override val numberOfItemsPerGroup = n
    }
  }

  // ------------------------------------------------------------------------
  trait Effect {
    def outputSizes: List[Int] = Nil
    def localBufferSizes: List[Int] = Nil
    override def toString = "Effect {out=" + outputSizes + "}"
  }

  type Effect1[A] = Function1[A,Effect]
  type Effect2[A1,A2] = Function2[A1,A2,Effect]
  type Effect3[A1,A2,A3] = Function3[A1,A2,A3,Effect]

  class SimpleGlobalArrayEffect1[B:FixedSizeMarshal, T: HasLength] extends Effect1[T] {
    def apply(a: T) = new Effect {
      override val outputSizes = (implicitly[HasLength[T]].length(a) * fixedSizeMarshal[B].size) :: Nil
    }
  }

  class SimpleGlobalArrayEffect2[B:FixedSizeMarshal, T: HasLength, U: HasLength] extends Effect2[T,U] {
    def apply(a1: T, a2: U) = new Effect {
      override val outputSizes = ((implicitly[HasLength[T]].length(a1) max implicitly[HasLength[U]].length(a2)) * fixedSizeMarshal[B].size) :: Nil
    }
  }

  class SimpleLocalArrayEffect1[A:FixedSizeMarshal, T: HasLength](localSizes: Int*) extends Effect1[T] {
    def apply(a: T) = new Effect {
      override val outputSizes = (implicitly[HasLength[T]].length(a) * fixedSizeMarshal[A].size) :: Nil
      override val localBufferSizes = localSizes.toList
    }
  }

  class SimpleLocalArrayWithOutputEffect1[B:FixedSizeMarshal, T: HasLength](numThreads: Int, localSizes:Int*) extends Effect1[T] {
    def apply(a: T) = new Effect {
      override val outputSizes = (((implicitly[HasLength[T]].length(a) + numThreads - 1) / numThreads) * fixedSizeMarshal[B].size) :: Nil
      override val localBufferSizes = localSizes.toList
    }
  }

  // ------------------------------------------------------------------------
  trait Kernel
  trait Kernel1[A,B] extends Function1[A,Future[B]] with Kernel
  trait Kernel2[A1,A2,B] extends Function2[A1,A2,Future[B]] with Kernel
  trait Kernel3[A1,A2,A3,B] extends Function3[A1,A2,A3,Future[B]] with Kernel

  // Kernels that were compiled from functions on single elements
  trait BBArrayMapKernel1[A,B] extends Kernel1[BBArray[A],BBArray[B]]
  trait BBArrayMapKernel2[A1,A2,B] extends Kernel2[BBArray[A1],BBArray[A2],BBArray[B]]
  trait BBArrayMapKernel3[A1,A2,A3,B] extends Kernel3[BBArray[A1],BBArray[A2],BBArray[A3],BBArray[B]]

  trait BBArrayReduceKernel1[A] extends Kernel1[BBArray[A],A]
  trait BBArrayLocalReduceKernel1[A,B] extends Kernel1[BBArray[A],BBArray[B]]

  // ------------------------------------------------------------------------
  // Abuse implicits for writing kernels
  // Not sure if this is a good idea
  implicit val id1: Id1 = null
  implicit val id2: Id2 = null
  implicit val id3: Id3 = null
  implicit def localIndexed1[A]: LocalIndexed1[A] = null
  implicit def localIndexed2[A]: LocalIndexed2[A] = null
  implicit def localIndexed3[A]: LocalIndexed3[A] = null
  implicit def blockIndexed1[A]: GroupIndexed1[A] = null
  implicit def blockIndexed2[A]: GroupIndexed2[A] = null
  implicit def blockIndexed3[A]: GroupIndexed3[A] = null

  implicit def force[B](f: Future[B]) = f.force
}
