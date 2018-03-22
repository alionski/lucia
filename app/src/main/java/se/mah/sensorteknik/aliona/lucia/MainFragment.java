package se.mah.sensorteknik.aliona.lucia;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;


/**
 * The main fragment that is shown when the app is opened.
 * Has the four buttons for controlling the Arduino device.
 */
public class MainFragment extends Fragment implements View.OnClickListener{
    private ImageButton mProximityButton, mLEDButton, mInfoButton, mBeepButton;
    private OnFragmentInteractionListener mListener;

    public static final int TOGGLE_LEDS = 0;
    public static final int TOGGLE_DISTANCE_SENSOR = 1;
    public static final int TOGGLE_BEEPING = 2;
    public static final int SHOW_INFO_FRAG = 3;

    private boolean ledOn = false;
    private boolean proximityOn = false;
    private boolean beepingOn = false;

    /**
     * Constructor. Required to be empty.
     */
    public MainFragment() {}

    /**
     * Standard static construction method.
     * @return A new instance of fragment MainFragment.
     */
    public static MainFragment newInstance() {
        MainFragment fragment = new MainFragment();
        return fragment;
    }

    /**
     * Standard method. Initialised the UI and returns the view.
     * @param inflater -- standard param
     * @param container -- MainActivity
     * @param savedInstanceState -- previous state if the fragment has been saved before
     * @return -- the inflated view.
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);
        initUI(view);
        return view;
    }

    /**
     * Associates xml id's with instance variables and sets click listeners.
     * @param view -- the fragment's view
     */
    private void initUI(View view) {
        mProximityButton = view.findViewById(R.id.button_proximity_sensor);
        mProximityButton.setContentDescription(getString(R.string.proximity_description, getString(R.string.function_off)));
        mLEDButton = view.findViewById(R.id.button_led_actuators);
        mLEDButton.setContentDescription(getString(R.string.led_description, getString(R.string.function_off)));
        mInfoButton = view.findViewById(R.id.button_information);
        mBeepButton = view.findViewById(R.id.button_text_to_speech);
        mBeepButton.setContentDescription(getString(R.string.beep_description, getString(R.string.function_off)));
        mProximityButton.setOnClickListener(this);
        mLEDButton.setOnClickListener(this);
        mBeepButton.setOnClickListener(this);
        mInfoButton.setOnClickListener(this);
    }

    /**
     * Standard onClick() interface method. Determines which button was clicked, changes the content description,
     * which will be voiced by TalkBack and delegates the events to the listener (MainController).
     * @param view -- the view that was clicked.
     */
    @Override
    public void onClick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        switch(view.getId()) {
            case R.id.button_information:
                mListener.onFragmentInteraction(SHOW_INFO_FRAG);
                break;
            case R.id.button_led_actuators:
                mListener.onFragmentInteraction(TOGGLE_LEDS);
                if(!ledOn) {
                    mLEDButton.setContentDescription(getString(R.string.led_description, getString(R.string.function_on)));
                } else {
                    mLEDButton.setContentDescription(getString(R.string.led_description, getString(R.string.function_off)));
                }
                ledOn = !ledOn;
                break;
            case R.id.button_proximity_sensor:
                mListener.onFragmentInteraction(TOGGLE_DISTANCE_SENSOR);
                if(!proximityOn) {
                    mProximityButton.setContentDescription(getString(R.string.proximity_description, getString(R.string.function_on)));
                } else {
                    mProximityButton.setContentDescription(getString(R.string.proximity_description, getString(R.string.function_off)));
                }
                proximityOn = !proximityOn;
                break;
            case R.id.button_text_to_speech:
                mListener.onFragmentInteraction(TOGGLE_BEEPING);
                if(!beepingOn) {
                    mBeepButton.setContentDescription(getString(R.string.beep_description, getString(R.string.function_on)));
                } else {
                    mBeepButton.setContentDescription(getString(R.string.beep_description, getString(R.string.function_off)));
                }
                beepingOn = !beepingOn;
                break;
        }
    }

    /**
     * Called from MainController; sets itself as the fragment's listener.
     * @param fragmentInteractionListener -- implementation of the listener interface.
     */
    public void setListener(OnFragmentInteractionListener fragmentInteractionListener) {
        this.mListener = fragmentInteractionListener;
    }

    /**
     * Callback implemented by MainController.
     */
    public interface OnFragmentInteractionListener {
        void onFragmentInteraction(int command);
    }


    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }
}
