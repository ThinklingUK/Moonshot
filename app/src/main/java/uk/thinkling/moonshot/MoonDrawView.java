package uk.thinkling.moonshot;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.view.GestureDetectorCompat;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import java.io.*;
import java.util.ArrayList;
import java.util.List;


import org.jbox2d.collision.shapes.EdgeShape;
import uk.thinkling.physics.MoveObj;
import uk.thinkling.physics.CollisionManager;


import org.jbox2d.callbacks.ContactImpulse;
import org.jbox2d.callbacks.ContactListener;
import org.jbox2d.collision.Manifold;
import org.jbox2d.collision.shapes.CircleShape;
import org.jbox2d.collision.shapes.PolygonShape;
import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.Body;
import org.jbox2d.dynamics.BodyDef;
import org.jbox2d.dynamics.BodyType;
import org.jbox2d.dynamics.FixtureDef;
import org.jbox2d.dynamics.World;
import org.jbox2d.dynamics.contacts.Contact;



/**
 * SIMPLES
 * Created by Ellison on 11/04/2015.
 */
public class MoonDrawView extends View {


    /*VARIABLES*/

    MoveObj inPlay;
    ArrayList<MoveObj> objs = new ArrayList<>();
    uk.thinkling.physics.CollisionManager collider;
    int screenW, screenH, bedH, coinR, startZone;
    int shadoff = 4; //shadow offset TODO - factor of coinR
    double gravity = 1, friction = 0.00; //0.07 is shove friction, 0.98 is gravity
    final float coinRatio = 0.33f; // bed to radius ratio (0.33 is 2 thirds)
    final float bedSpace=0.8f; // NB: includes end space and free bed before first line.
    int beds=9, maxCoins=5, bedScore=3;
    int coinsLeft = 0, winner = -1;
    int playerNum = 0;
    int[][][] score = new int[2][beds+2][2]; // [player][bed - bed zero is for point score and final bed is for tracking completed][actual|potential]
    String[] pName = new String[2];
    boolean sounds=true, bounds=true, rebounds=true, highlight=true;

    String dynamicInstructions ="";

    MainActivity parent;
    private final GestureDetectorCompat gdc;

    static Paint linepaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    static Paint outlinepaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    static Paint shadowpaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint bmppaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    static Bitmap rawbmp, bmp; // bitmap for the coin
    Matrix matrix = new Matrix(); //matrix for bitmap


    /*VARIABLES*/


    // this is the constructor - it is called when an instance of this class is created
    public MoonDrawView(Context context, AttributeSet attrs) {
        super(context, attrs);
        //if (!isInEditMode()) ;
        parent = (MainActivity) this.getContext(); //TODO - remove all references to parent to improve editor preivew
        gdc = new GestureDetectorCompat(parent, new MyGestureListener()); // create the gesture detector
       // for (int f = 0; f <= score.length; f++) score[0][f][0] = score[0][f][1] = score[1][f][0] = score[1][f][1] = 0; /* set scores to zero */
    }


    // this happens if the screen size changes - including the first time - it is measured. Here is where we get width and height
    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        screenW = w;
        screenH = h;

        float strokeSize = (w/180);  // NB this is driven by width so set in onSizeChanged
        shadowpaint.setARGB(64, 0, 0, 0);
        linepaint.setColor(Color.parseColor("#CD7F32"));
        linepaint.setStyle(Paint.Style.STROKE);
        linepaint.setStrokeWidth(strokeSize); //TODO set based on screensize
        linepaint.setTextSize(30);
        linepaint.setDither(true);                    // set the dither to true
        linepaint.setStyle(Paint.Style.STROKE);       // set to STOKE
        //linepaint.setStrokeJoin(Paint.Join.ROUND);    // set the join to round you want
        linepaint.setStrokeCap(Paint.Cap.ROUND);      // set the paint cap to round too
        // linepaint.setPathEffect(new CornerPathEffect(10) );   // set the path effect when they join.
        linepaint.setAntiAlias(true);
        outlinepaint.set(linepaint);
        outlinepaint.setColor(Color.parseColor("#FFFFFF"));

        try {
            loadPrefs();
        } catch (Exception e){
            //Log.e("LOADING PREFS",  e.getMessage());
        }

        collider = new CollisionManager(w, h, friction, gravity);
       // collider.addBoundary(w / 3 * 2, h / 3 * 2, w / 2, h / 2); // add a boundary

        try {
            restoreData();
        } catch (Exception ex){ //could be FileNotFoundException, IOException, ClassNotFoundException
            //Log.e("deserialise",ex.toString());
        }



    }

    /* BIT FOR TOUCHING! */
    @Override
    public boolean onTouchEvent(@NonNull final MotionEvent e) {
        return gdc.onTouchEvent(e);
    }

    class MyGestureListener extends GestureDetector.SimpleOnGestureListener {
        private static final String DEBUG_TAG = "Gestures";
        private final int SWIPE_MIN_DISTANCE = 120;
        private final int SWIPE_THRESHOLD_VELOCITY = 200;

        @Override
        public boolean onDown(MotionEvent e) {
            //add a state tracker - could also require hit on a coin at start
            if (inPlay.state == 1 && e.getY()> startZone) {
                inPlay.xSpeed = inPlay.ySpeed = 0;
                inPlay.x = e.getX();
                inPlay.y = e.getY();
            }
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            // effectively a Drag detector - although would need to check that we have hit a ball first in onDown
            if (inPlay.state == 1) {
                if (e2.getY() > startZone) {
                    inPlay.x = e2.getX();
                    inPlay.y = e2.getY();
                    inPlay.xSpeed = inPlay.ySpeed = 0;
                } else {
                    // gets a speed on drag
                    inPlay.xSpeed = (e2.getX()-e1.getX())/10;
                    inPlay.ySpeed = (e2.getY()-e1.getY())/10;
                }
            }
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (inPlay.state == 1 && e2.getY()>startZone) {
                inPlay.xSpeed = velocityX / 25;
                inPlay.ySpeed = velocityY / 25;
                inPlay.rSpeed = Math.random()*20-10;
            }
            return true;
        }
    }



    @Override
    // this is the method called when the view needs to be redrawn
    protected void onDraw(Canvas canvas) {

        super.onDraw(canvas);

        /*Score's Text*/
        if (coinsLeft >1)
            parent.HighScoreText.setText(pName[playerNum] + " - " + (Math.min(maxCoins,coinsLeft))+" coins");
        else
            parent.HighScoreText.setText(pName[playerNum] + " - Last coin"); //TODO const etc

        for (MoveObj obj : objs) {
            obj.draw(canvas);
            // canvas.drawCircle((float) obj.x+shadoff, (float) obj.y+shadoff, obj.radius, obj.);
/*            matrix.reset();
            matrix.postTranslate(-coinR, -coinR);
            matrix.postRotate(obj. angle);
            matrix.postTranslate((float) obj.x, (float) obj.y);
            canvas.drawBitmap(bmp, matrix, bmppaint);*/

        }
        for (CollisionManager.Boundary bound : collider.boundaries) {
            canvas.drawCircle((float) bound.x, (float) bound.y, bound.width/2,linepaint );
        }
    }


    // this is the method called when recalculating positions
    public void update() {

        float volume;
        // handle all the collisions starting at earliest time - if 2nd obj is null, then a wall collision
        // Collision manager also moves the objects.
        collider.collide(objs);
        for (CollisionManager.CollisionRec coll : collider.collisions) {
            //TODO set pitch based on size of the objects.
            volume = Math.min((float) coll.impactV / 100, 1); //set the volume based on impact speed
            if (coll.objb == null) {  //if a wall collision
                // if a wall collision, play sound and may void the coin
                if (sounds) parent.player.play(parent.clunkSound, volume, volume, 2, 0, 1);
                if (bounds) coll.obja.state=-1; // if boundary rules apply, set to void
            } else {
                if (sounds) parent.player.play(parent.clinkSound, volume, volume, 2, 0, 1);
            }
        }



        // Once the collisions have been handled, draw each object and apply friction
        boolean motion = false;
        for (MoveObj obj : objs) {

            //if outside the sidebars and boundary rules are on, then void the coin if already in playzone
            if (bounds && obj.state==0 && (obj.x-coinR<bedH || obj.x+coinR>screenW-bedH)) obj.state=-1;

            if (sounds) {
                if (obj.xSpeed != 0 || obj.ySpeed != 0) {
                    motion = true;
                    // if there is a streamID then adjust volume else start movement sound
                    volume = Math.min((float) Math.sqrt(obj.xSpeed * obj.xSpeed + obj.ySpeed * obj.ySpeed) / 50, 1); //set the volume based on impact speed TODO const or calc

                    if (obj.movingStreamID > 0) {
                        parent.player.setVolume(obj.movingStreamID, volume, volume);
                        //adjust volume
                    } else {
                        obj.movingStreamID = parent.player.play(parent.slideSound, volume, volume, 1, -1, 1);
                    }
                } else {
                    //stop any playing sound
                    if (obj.movingStreamID > 0) {
                        parent.player.stop(obj.movingStreamID);
                        obj.movingStreamID = 0;
                    }
                }
            }
        }

        //if there is no ball, or a ball in play and all motion stops, calc intermediate or final scores and play new ball if final
        if (inPlay == null) {

            // add a new coin - this could be first coin
            // TODO in combat mode we alternate playerNum
            inPlay = new MoveObj(11 + playerNum, coinR, screenW / 2, screenH /2, 5, 0);
            inPlay.wallBounce=rebounds; //enable or disable wall bounce TODO - move into constructor
            MoveObj x = new MoveObj(10, screenW / 3, screenW / 2, screenH /2, 0, 0);
            x.mass *= 100000000;
            objs.add(x);
            for (int i = 0; i < maxCoins*4 ; i++) {
                objs.add(new MoveObj(6, screenW, screenH));
            }
            objs.add(inPlay);

            if (sounds) parent.player.play(parent.placeSound,1,1,1,0,1);
        }

    }

    public void saveData() throws IOException {
        File file = new File(getContext().getCacheDir(), "moveObjs");
        ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(file));
        os.writeObject(objs);
        os.close();
        file = new File(getContext().getCacheDir(), "Scores");
        os = new ObjectOutputStream(new FileOutputStream(file));
        os.writeObject(score);
        os.close();
    }

    public void restoreData() throws IOException,ClassNotFoundException {
        coinsLeft = maxCoins+1; // this only gets used if no restore. NB: will get reduced by one if new game
        File file = new File(getContext().getCacheDir(), "moveObjs");
        ObjectInputStream is = new ObjectInputStream(new FileInputStream(file));
        objs = (ArrayList) is.readObject();
        int lastCoinPlayed = objs.size()-1;
        if (lastCoinPlayed >= 0) {
            inPlay = objs.get(lastCoinPlayed);
            playerNum=inPlay.type-11;
        }
        coinsLeft=maxCoins-lastCoinPlayed;
        file = new File(getContext().getCacheDir(), getContext().getString(R.string.Score_File_Name));
        is = new ObjectInputStream(new FileInputStream(file));
        score = (int[][][]) is.readObject();
    }

    // TODO - pull in the cache here - rather than the onStart()
    public void loadPrefs() throws ClassCastException  {
        //Load lists from file or set defaults TODO set defaults as consts
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        pName[0] = preferences.getString("pref_player1", "Player 1");
        pName[1] = preferences.getString("pref_player2", "Player 2");

        sounds = preferences.getBoolean("pref_sounds", true);
        bounds = preferences.getBoolean("pref_bounds", true);
        rebounds = preferences.getBoolean("pref_rebounds", true);
        highlight = preferences.getBoolean("pref_highlight", true);


        maxCoins = Integer.parseInt(preferences.getString("pref_maxcoins", "5"));
        bedScore = Integer.parseInt(preferences.getString("pref_bedscore", "3"));
        gravity =  Integer.parseInt(preferences.getString("pref_gravity", "0"))/100;
        friction =  Integer.parseInt(preferences.getString("pref_friction","0"))/100;
        beds = Integer.parseInt(preferences.getString("pref_beds", "9"));

        //Number of beds then affects bed size, coin size etc.

        bedH = Math.round(screenH * bedSpace / (beds + 3)); //2 extra beds for end and free space after flickzone
        coinR=Math.round(bedH * coinRatio);
        startZone=(beds+2)*bedH+coinR;

        rawbmp = BitmapFactory.decodeResource(getResources(), R.drawable.coin67);
        bmp = Bitmap.createScaledBitmap(rawbmp, coinR * 2, coinR * 2, true);
        bmppaint.setFilterBitmap(true);

        //TODO if #beds changed (ie. in prefs) and mismatch with saved data, reset score data

        dynamicInstructions = String.format(getContext().getString(R.string.str_instructions), beds, maxCoins, bedScore, rebounds?"bounce":"fall", bounds?"not be":"be");

        //Also if bedScore changes then scoring might fail - best to restart in these cases - or all cases?
        if (score[0].length!=beds+2) score = new int[2][beds+2][2];

        jbox();

    }






    public void jbox( )
    {
        // Define the gravity vector.
        Vec2 gravity = new Vec2( 0, -10 );

        // Initialise the World.
        World world = new World( gravity);

        // Set a contact listener.
        world.setContactListener( new ContactListener()
        {
            @Override
            public void preSolve( Contact arg0, Manifold arg1 )
            {
            }

            @Override
            public void postSolve( Contact arg0, ContactImpulse arg1 )
            {
            }

            @Override
            public void endContact( Contact arg0 )
            {
            }

            @Override
            public void beginContact( Contact arg0 )
            {
                // Ball collided with container!
                System.out.println( "Bounce" );
            }
        } );

        // Create the ground (Something for dynamic bodies to collide with).
        {
            BodyDef groundBodyDef = new BodyDef();
            groundBodyDef.position.set( 0, 0 );
            groundBodyDef.type = BodyType.STATIC;

            // Create the Body in the World.
            Body ground = world.createBody( groundBodyDef );

            // Create the fixtures (physical aspects) of the ground body.
            FixtureDef groundEdgeFixtureDef = new FixtureDef();
            groundEdgeFixtureDef.density = 1.0f;
            groundEdgeFixtureDef.friction = 1.0f;
            groundEdgeFixtureDef.restitution = 0.4f;

            EdgeShape groundEdge = new EdgeShape();
            groundEdgeFixtureDef.shape = groundEdge;

            // We will create the ground as a box. Creating each edge of the box
            // in turn. The reason we don't use groundEdge.setAsBox() method is
            // because this creates a solid fixture which does not allow other
            // bodies to exist within it. When we create the ground with edges
            // this will give us a nice container to have our bodies bounce
            // around in.

            // Bottom Edge.
            groundEdge.set(new Vec2(0, 0), new Vec2(10, 0));
            ground.createFixture( groundEdgeFixtureDef );

            // Reuse the PolygonShape and FixtureDef objects since they are only
            // used to create objects in the World. Think of them as being a
            // cookie cutter. Once you make cut a cookie it does matter what
            // happens to the cookie cutter after.

            // Right Edge.
            groundEdge.set( new Vec2( 10, 0 ), new Vec2( 10, 10 ) );
            ground.createFixture( groundEdgeFixtureDef );

            // Top Edge.
            groundEdge.set( new Vec2( 10, 10 ), new Vec2( 0, 10 ) );
            ground.createFixture( groundEdgeFixtureDef );

            // Left Edge.
            groundEdge.set( new Vec2( 0, 10 ), new Vec2( 0, 0 ) );
            ground.createFixture( groundEdgeFixtureDef );
        }

        // Create a Ball.
        BodyDef ballBodyDef = new BodyDef();
        ballBodyDef.type = BodyType.DYNAMIC;
        ballBodyDef.position.set( 5, 5 ); // Centre of the ground box.

        // Create the body for the ball within the World.
        Body ball = world.createBody( ballBodyDef );

        // Create the actual fixture representing the box.
        CircleShape ballShape = new CircleShape();
        ballShape.m_radius = 0.5f; // Diameter of 1m.
        // ballShape.m_p is the offset relative to body. Default of (0,0)

        FixtureDef ballFixtureDef = new FixtureDef();
        ballFixtureDef.density = 1.0f; // Must have a density or else it won't
        // be affected by gravity.
        ballFixtureDef.restitution = 0.8f; // Define how bouncy the ball is.
        ballFixtureDef.friction = 0.2f;
        ballFixtureDef.shape = ballShape;

        // Add the fixture to the ball body.
        ball.createFixture( ballFixtureDef );

        // Do a few steps of simulation.
        for( int i = 0; i < 10; ++i )
        {
            System.out.println( ball.getPosition() );

            // Advance the world 1/6 of a second into the future.
            // Typically you should use 1/60 of a second for simulating physics
            // though...
            world.step( 1 / 6f, 8, 3 );
        }
    }
}
