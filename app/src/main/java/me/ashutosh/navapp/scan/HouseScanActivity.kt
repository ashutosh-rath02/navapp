package me.ashutosh.navapp.scan
import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.opengl.GLSurfaceView
import com.google.ar.core.*
import me.ashutosh.navapp.mapping.OccupancyGrid
import me.ashutosh.navapp.persistence.MapStore
class HouseScanActivity:Activity(){
 private val grid=OccupancyGrid(400,400,0.05f);private lateinit var gl:GLSurfaceView;private lateinit var renderer:HouseScanRenderer;private lateinit var status:TextView;private lateinit var map:MapView;private lateinit var cloudView:PointCloudView
 private var session:Session?=null;private var installRequested=false
 override fun onCreate(saved:Bundle?){super.onCreate(saved);val root=FrameLayout(this)
  gl=GLSurfaceView(this).apply{preserveEGLContextOnPause=true;setEGLContextClientVersion(2);renderer=HouseScanRenderer(this@HouseScanActivity,grid){m->runOnUiThread{status.text=m;map.invalidate();if(::cloudView.isInitialized)cloudView.invalidate()}};setRenderer(renderer);renderMode=GLSurfaceView.RENDERMODE_CONTINUOUSLY}
  root.addView(gl,FrameLayout.LayoutParams(-1,-1));cloudView=PointCloudView(this){renderer.pointSnapshot()};cloudView.visibility=View.GONE;root.addView(cloudView,FrameLayout.LayoutParams(-1,-1));status=TextView(this).apply{text="Starting ARCore…";textSize=14f;setTextColor(Color.WHITE);setPadding(16,14,16,14);setBackgroundColor(0xAA101719.toInt())}
  root.addView(status,FrameLayout.LayoutParams(-1,-2,Gravity.TOP));map=MapView(this,grid);root.addView(map,FrameLayout.LayoutParams(180,180,Gravity.TOP or Gravity.END).apply{topMargin=64;rightMargin=10})
  val bar=LinearLayout(this).apply{gravity=Gravity.CENTER;setBackgroundColor(0xCC101719.toInt())}
  bar.addView(Button(this).apply{text="3D View";setOnClickListener{cloudView.visibility=if(cloudView.visibility==View.VISIBLE)View.GONE else View.VISIBLE; text=if(cloudView.visibility==View.VISIBLE)"Camera" else "3D View";cloudView.invalidate()}})
  bar.addView(Button(this).apply{text="Pause";setOnClickListener{renderer.scanning=!renderer.scanning;text=if(renderer.scanning)"Pause" else "Resume"}})
  bar.addView(Button(this).apply{text="Save map";setOnClickListener{runCatching{MapStore.save(this@HouseScanActivity,grid);Toast.makeText(this@HouseScanActivity,"Map saved inside NavApp",Toast.LENGTH_LONG).show()}.onFailure{Toast.makeText(this@HouseScanActivity,it.message,Toast.LENGTH_LONG).show()}}})
  bar.addView(Button(this).apply{text="Reset";setOnClickListener{grid.clear();renderer.resetOrigin();map.invalidate();status.text="Reset · walk to begin a new scan"}})
  root.addView(bar,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));setContentView(root);setupAr()}
 private fun setupAr(){try{when(ArCoreApk.getInstance().requestInstall(this,!installRequested)){ArCoreApk.InstallStatus.INSTALL_REQUESTED->{installRequested=true;status.text="Install Google Play Services for AR and reopen NavApp";return};ArCoreApk.InstallStatus.INSTALLED->Unit}
  val s=Session(this);val c=Config(s);if(!s.isDepthModeSupported(Config.DepthMode.AUTOMATIC)){status.text="ARCore Depth is not supported";s.close();return}
  c.depthMode=Config.DepthMode.AUTOMATIC;c.updateMode=Config.UpdateMode.LATEST_CAMERA_IMAGE;s.configure(c);session=s;renderer.session=s;status.text="Depth ready · walk slowly and scan all rooms"
 }catch(e:Exception){status.text="ARCore setup failed: "+e.javaClass.simpleName+" "+e.message}}
 override fun onResume(){super.onResume();if(session==null&&::status.isInitialized)setupAr();try{session?.resume();gl.onResume()}catch(e:Exception){status.text="Resume failed: "+e.message}}
 override fun onPause(){gl.onPause();session?.pause();super.onPause()}
 override fun onDestroy(){session?.close();session=null;super.onDestroy()}
}
private class MapView(ctx:android.content.Context,val grid:OccupancyGrid):View(ctx){private val p=Paint(3)
 override fun onDraw(c:Canvas){val w=width.toFloat();val h=height.toFloat();p.color=0xEE101719.toInt();c.drawRect(0f,0f,w,h,p);val a=grid.snapshot();val cw=w/grid.width;val ch=h/grid.height
  for(y in 0 until grid.height step 2)for(x in 0 until grid.width step 2){val v=a[y*grid.width+x];if(kotlin.math.abs(v)>.12f){p.color=if(v>0)0xFFFF766E.toInt() else 0xFF6C9B8F.toInt();c.drawRect(x*cw,y*ch,(x+2)*cw,(y+2)*ch,p)}}
  p.color=Color.WHITE;c.drawCircle(w*grid.originX/grid.width,h*grid.originY/grid.height,4f,p)}
}