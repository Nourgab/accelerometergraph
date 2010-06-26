package jp.co.laurus.android.accelerometergraph;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;

public class MainActivity extends Activity {

	private static final String TAG = "Accelerometer Graph";

	private static final int STATUS_START = 1;
	private static final int STATUS_STOP = 2;

	private int mSensorDelay = SensorManager.SENSOR_DELAY_UI;
	
	private final float[] mCurrents = new float[4];
	private final ConcurrentLinkedQueue<float[]> mHistory = new ConcurrentLinkedQueue<float[]>();

	private GraphView mGraphView;
	private SensorManager mSensorManager;
	private Sensor mAccelerometer;
	
	private int mMaxHistorySize = 250;
	private boolean mDrawRoop = true;
	private int mDrawDelay = 100;
	private int mLineWidth = 2;
	private int mGraphScale = 5;
	private int mZeroLineY = 200;
	private int mZeroLineYOffset = 0;
	private float mTouchOffset;
	private int mStatus = STATUS_START;

	private final SensorEventListener mSensorEventListener = new SensorEventListener() {
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
			Log.i(TAG, "mSensorEventListener.onAccuracyChanged()");
		}

		@Override
		public void onSensorChanged(SensorEvent event) {
			Log.i(TAG, "mSensorEventListener.onSensorChanged()");
			
			for (int i = 0; i < 3; i++) {
				mCurrents[i] = event.values[i];
			}
			
			// TODO 実加速度の計算
			
			synchronized (this) {
				if (mHistory.size() > mMaxHistorySize) {
					mHistory.poll();
				}
				mHistory.add(mCurrents.clone());
			}
		}
	};

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.i(TAG, "MainActivity.onCreate()");

		setContentView(R.layout.main);

		mGraphView = new GraphView(this);
		FrameLayout frame = (FrameLayout) findViewById(R.id.frame);
		frame.addView(mGraphView);

		final RadioButton sensorDelayFastestBtn = new RadioButton(this);
		sensorDelayFastestBtn.setText("FASTEST");

		final RadioButton sensorDelayGameBtn = new RadioButton(this);
		sensorDelayGameBtn.setText("GAME");

		final RadioButton sensorDelayUiBtn = new RadioButton(this);
		sensorDelayUiBtn.setText("UI");

		final RadioButton sensorDelayNormalBtn = new RadioButton(this);
		sensorDelayNormalBtn.setText("NORMAL");
		
		RadioGroup sensorDelayGroup = new RadioGroup(this);
		sensorDelayGroup.setOrientation(RadioGroup.VERTICAL);
		sensorDelayGroup.addView(sensorDelayFastestBtn);
		sensorDelayGroup.addView(sensorDelayGameBtn);
		sensorDelayGroup.addView(sensorDelayUiBtn);
		sensorDelayGroup.addView(sensorDelayNormalBtn);
		sensorDelayGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				if (checkedId == sensorDelayFastestBtn.getId()) {
					mSensorDelay = SensorManager.SENSOR_DELAY_FASTEST;
				} else if (checkedId == sensorDelayGameBtn.getId()) {
					mSensorDelay = SensorManager.SENSOR_DELAY_GAME;
				} else if (checkedId == sensorDelayUiBtn.getId()) {
					mSensorDelay = SensorManager.SENSOR_DELAY_UI;
				} else if (checkedId == sensorDelayNormalBtn.getId()) {
					mSensorDelay = SensorManager.SENSOR_DELAY_NORMAL;
				}
			}
		});
		sensorDelayGroup.check(sensorDelayUiBtn.getId());
		frame.addView(sensorDelayGroup);
	}

	@Override
	protected void onStart() {
		Log.i(TAG, "MainActivity.onStart()");

		// 初期化
		mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		List<Sensor> sensors = mSensorManager
				.getSensorList(Sensor.TYPE_ACCELEROMETER);
		if (sensors.size() > 0) {
			mAccelerometer = sensors.get(0);
		} else {
			Log.e(TAG, "加速度センサーが見つかりませんでした");
		}
		
		super.onStart();
	}

	@Override
	protected void onResume() {
		Log.i(TAG, "MainActivity.onResume()");

		// センサーリスナーを登録
		if (mAccelerometer != null) {
			mSensorManager.registerListener(mSensorEventListener, mAccelerometer, mSensorDelay);
		}

		if (!mDrawRoop) {
			// グラフの描画を再開
			mDrawRoop = true;
			mGraphView.surfaceCreated(mGraphView.getHolder());
		}

		super.onResume();
	}
	
	@Override
	protected void onPause() {
		Log.i(TAG, "MainActivity.onPause()");

		// センサーリスナーを解除
		mSensorManager.unregisterListener(mSensorEventListener);

		// グラフの描画を止める
		mDrawRoop = false;

		super.onPause();
	}
	
	@Override
	protected void onStop() {
		Log.i(TAG, "MainActivity.onStop()");
		
		mSensorManager = null;
		mAccelerometer = null;

		super.onStop();
	}
	
	@Override
	protected void onDestroy() {
		Log.i(TAG, "MainActivity.onDestroy()");
		
		// TODO 自分のプロセスをキル
		
		super.onDestroy();
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		switch (event.getAction()) {
		case KeyEvent.ACTION_DOWN:
			switch (event.getKeyCode()) {
			case KeyEvent.KEYCODE_VOLUME_UP:
				mGraphScale++;
				return true;
			case KeyEvent.KEYCODE_VOLUME_DOWN:
				if (mGraphScale > 1) {
					mGraphScale--;
				}
				return true;
			case KeyEvent.KEYCODE_FOCUS:
				if (mStatus == STATUS_START) {
					mSensorManager.unregisterListener(mSensorEventListener);
					mStatus = STATUS_STOP;
				} else {				
					if (mAccelerometer != null) {
						mSensorManager.registerListener(mSensorEventListener, mAccelerometer, mSensorDelay);
					}
					mStatus = STATUS_START;
				}
				return true;
			case KeyEvent.KEYCODE_CAMERA:
				return true;
			}
		case KeyEvent.ACTION_UP:
			switch (event.getKeyCode()) {
			case KeyEvent.KEYCODE_VOLUME_UP:
			case KeyEvent.KEYCODE_VOLUME_DOWN:
			case KeyEvent.KEYCODE_FOCUS:
			case KeyEvent.KEYCODE_CAMERA:
				return true;
			}
		}
		return super.dispatchKeyEvent(event);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			mTouchOffset = event.getY();
			break;
		case MotionEvent.ACTION_UP:
			mZeroLineY += mZeroLineYOffset;
			mZeroLineYOffset = 0;
			break;
		case MotionEvent.ACTION_MOVE:
			mZeroLineYOffset = (int) (event.getY() - mTouchOffset);
			break;
		}
		return super.onTouchEvent(event);
	}

	private class GraphView extends SurfaceView implements SurfaceHolder.Callback,Runnable {

		private Thread mThread;
		private SurfaceHolder mHolder;
		private final int[] mXPos = {100, 200, 300, 400}; 

		public GraphView(Context context) {
			super(context);
			
			Log.i(TAG, "GraphView.GraphView()");
			
			mHolder = getHolder();
			mHolder.addCallback(this);
			
			setFocusable(true);
			requestFocus();
		}

		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width,
				int height) {
			Log.i(TAG, "GraphView.surfaceChanged()");
		}

		@Override
		public void surfaceCreated(SurfaceHolder holder) {
			Log.i(TAG, "GraphView.surfaceCreated()");
			mThread = new Thread(this);
			mThread.start();
		}

		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			Log.i(TAG, "GraphView.surfaceDestroyed()");
			
			boolean roop = true;
			while (roop) {
				try {
					mThread.join();
					roop = false;
				} catch (InterruptedException e) {
					Log.e(TAG, e.getMessage());
				}
			}
			mThread = null;
		}

		@Override
		public void run() {
			Log.i(TAG, "GraphView.run()");
			
			int width = getWidth();
			mMaxHistorySize = width / mLineWidth;

			Paint textPaint = new Paint();
			textPaint.setARGB(255, 255, 255, 255);
			textPaint.setAntiAlias(true);
			textPaint.setTextSize(14);
			
			Paint zeroLinePaint = new Paint();
			zeroLinePaint.setARGB(100, 255, 255, 255);
			zeroLinePaint.setAntiAlias(true);
			
			Paint[] linePaints = new Paint[4];
			linePaints[0] = new Paint();
			linePaints[0].setARGB(255, 64, 255, 255);
			linePaints[0].setAntiAlias(true);
			linePaints[0].setStrokeWidth(2);
			linePaints[1] = new Paint();
			linePaints[1].setARGB(255, 255, 64, 255);
			linePaints[1].setAntiAlias(true);
			linePaints[1].setStrokeWidth(2);
			linePaints[2] = new Paint();
			linePaints[2].setARGB(255, 255, 255, 64);
			linePaints[2].setAntiAlias(true);
			linePaints[2].setStrokeWidth(2);
			linePaints[3] = new Paint();
			linePaints[3].setARGB(255, 255, 255, 255);
			linePaints[3].setAntiAlias(true);
			linePaints[3].setStrokeWidth(2);

			while (mDrawRoop) {
				Canvas canvas = mHolder.lockCanvas();
				
				canvas.save();
				canvas.drawColor(Color.DKGRAY);
				
				float zeroLineY = mZeroLineY + mZeroLineYOffset;

				synchronized(mHolder) {
					canvas.drawLine(0, zeroLineY, width, zeroLineY, zeroLinePaint);

					for (int i = 0; i < 3; i++) {
						canvas.drawText(String.valueOf(mCurrents[i]), mXPos[i], 50, textPaint);
					}
					
					if (mHistory.size() > 1) {
						Iterator<float[]> iterator = mHistory.iterator();
						float[] before = new float[4];
						int x = width - mHistory.size() * mLineWidth;
						int beforeX = x;
						x += mLineWidth;
						
						if (iterator.hasNext()) {
							float[] history = iterator.next();
							for (int angle = 0; angle < 3; angle++) {
								before[angle] = zeroLineY - (history[angle] * mGraphScale);
							}
							while (iterator.hasNext()) {
								history = iterator.next();
								for (int angle = 0; angle < 3; angle++) {
									float startY = zeroLineY - (history[angle] * mGraphScale);
									float stopY = before[angle];
									canvas.drawLine(x, startY, beforeX, stopY, linePaints[angle]);
									before[angle] = startY;
								}
								beforeX = x;
								x += mLineWidth;
							}
						}
					}
				}
				
				canvas.restore();
	
				mHolder.unlockCanvasAndPost(canvas);
				
				try {
					Thread.sleep(mDrawDelay);
				} catch (InterruptedException e) {
					Log.e(TAG, e.getMessage());
				}
			}
		}
	}
}