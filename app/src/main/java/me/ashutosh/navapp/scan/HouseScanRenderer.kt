package me.ashutosh.navapp.scan
import android.content.Context
import android.opengl.*
import android.os.SystemClock
import com.google.ar.core.*
import me.ashutosh.navapp.mapping.OccupancyGrid
import java.nio.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
class HouseScanRenderer(val grid:OccupancyGrid,private val report:(String)->Unit):GLSurfaceView.Renderer{
 @Volatile var session:Session?=null;@Volatile var scanning=true
 private var texture=0;private var bound=false;private var program=0;private var pos=0;private var tex=0;private var sampler=0;private var origin:Pose?=null;private var last=0L;private var frames=0
 private val vertices=buf(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f));private val uv=FloatArray(8)
 fun resetOrigin(){origin=null}
 override fun onSurfaceCreated(g:GL10?,c:EGLConfig?){val ids=IntArray(1);GLES20.glGenTextures(1,ids,0);texture=ids[0];GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture)
  GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
  GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
  program=makeProgram();pos=GLES20.glGetAttribLocation(program,"p");tex=GLES20.glGetAttribLocation(program,"t");sampler=GLES20.glGetUniformLocation(program,"s")}
 override fun onSurfaceChanged(g:GL10?,w:Int,h:Int){GLES20.glViewport(0,0,w,h)}
 override fun onDrawFrame(g:GL10?){val s=session?:run{GLES20.glClear(0x4000);return}
  try{if(!bound){s.setCameraTextureName(texture);bound=true};val f=s.update();draw(f)
   if(f.camera.trackingState!=TrackingState.TRACKING){report("Tracking state: "+f.camera.trackingState);return};frames++
   if(scanning&&SystemClock.elapsedRealtime()-last>180){mapFrame(f);last=SystemClock.elapsedRealtime()}
   if(frames%30==0)report((if(scanning)"Scanning" else "Paused")+" · "+frames+" frames")
  }catch(e:NotYetAvailableException){report("Waiting for depth · move the phone slowly")}catch(e:Exception){report("Scanner error: "+e.javaClass.simpleName+" "+e.message)}}
 private fun mapFrame(f:Frame){val cam=f.camera;if(origin==null){origin=cam.pose;report("Origin set · walk through the house")};val o=origin?:return
  val image=try{f.acquireDepthImage16Bits()}catch(_:NotYetAvailableException){return}
  image.use{im->val pl=im.planes[0];val b=pl.buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);val intr=cam.imageIntrinsics
   val focal=FloatArray(2);val pp=FloatArray(2);val dims=IntArray(2);intr.getFocalLength(focal,0);intr.getPrincipalPoint(pp,0);intr.getImageDimensions(dims,0);if(focal[0]<=0f||focal[1]<=0f)return
   val cp=o.inverse().transformPoint(cam.pose.translation);val floor=o.translation[1]-1.2f;val sx=dims[0].toFloat()/im.width;val sy=dims[1].toFloat()/im.height
   for(v in 8 until im.height step 8)for(u in 8 until im.width step 8){val off=v*pl.rowStride+u*pl.pixelStride;if(off+1>=b.limit())continue
    val mm=(b.getShort(off).toInt() and 0xffff) and 0x1fff;if(mm !in 180..4500)continue;val z=mm/1000f
    val x=(u*sx-pp[0])/focal[0]*z;val y=-(v*sy-pp[1])/focal[1]*z;val world=cam.pose.transformPoint(floatArrayOf(x,y,-z))
    if(world[1]<floor+0.1f||world[1]>floor+2.4f)continue;val local=o.inverse().transformPoint(world);val mx=local[0];val my=-local[2]
    if(abs(mx)<5.7f&&abs(my)<5.7f)grid.updateRay(cp[0],-cp[2],mx,my,true)
   }}
 }
 private fun draw(f:Frame){f.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f),Coordinates2d.TEXTURE_NORMALIZED,uv)
  val t=buf(uv);GLES20.glUseProgram(program);GLES20.glEnableVertexAttribArray(pos);GLES20.glEnableVertexAttribArray(tex);vertices.position(0);t.position(0)
  GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,0,vertices);GLES20.glVertexAttribPointer(tex,2,GLES20.GL_FLOAT,false,0,t)
  GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture);GLES20.glUniform1i(sampler,0);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
  GLES20.glDisableVertexAttribArray(pos);GLES20.glDisableVertexAttribArray(tex)}
 private fun makeProgram():Int{fun sh(type:Int,src:String):Int{val id=GLES20.glCreateShader(type);GLES20.glShaderSource(id,src);GLES20.glCompileShader(id);val ok=IntArray(1);GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,ok,0);check(ok[0]==1){GLES20.glGetShaderInfoLog(id)};return id}
  val p=GLES20.glCreateProgram();GLES20.glAttachShader(p,sh(GLES20.GL_VERTEX_SHADER,"attribute vec2 p;attribute vec2 t;varying vec2 uv;void main(){gl_Position=vec4(p,0.,1.);uv=t;}"))
  GLES20.glAttachShader(p,sh(GLES20.GL_FRAGMENT_SHADER,"#extension GL_OES_EGL_image_external : require\nprecision mediump float;uniform samplerExternalOES s;varying vec2 uv;void main(){gl_FragColor=texture2D(s,uv);}"));GLES20.glLinkProgram(p)
  val ok=IntArray(1);GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);check(ok[0]==1){GLES20.glGetProgramInfoLog(p)};return p}
 private fun buf(a:FloatArray):FloatBuffer=ByteBuffer.allocateDirect(a.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(a);position(0)}
}