package se.mah.sensorteknik.aliona.lucia;

import android.content.Context;
import android.media.Image;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;


/**
 * Activities that contain this fragment must implement the
 * {@link MainFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link MainFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class MainFragment extends Fragment implements View.OnClickListener{
    private ImageButton mProximityButton, mLEDButton, mInfoButton, mBeepButton;
    private OnFragmentInteractionListener mListener;

    public static final int TOGGLE_LEDS = 0;
    public static final int TOGGLE_DISTANCE_SENSOR = 1;
    public static final int TOGGLE_BEEPING = 2;

    private boolean ledOn = false;
    private boolean proximityOn = false;
    private boolean beepingOn = false;

    /**
     * Constructor. Required to be empty.
     */
    public MainFragment() {}

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment MainFragment.
     */
    public static MainFragment newInstance() {
        MainFragment fragment = new MainFragment();
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);
        initUI(view);
        return view;
    }

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

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onClick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        switch(view.getId()) {
            case R.id.button_information:
                break;
            case R.id.button_led_actuators:
                mListener.onFragmentInteraction(TOGGLE_LEDS);
                if(!ledOn) {
                    mLEDButton.setContentDescription(getString(R.string.led_description, getString(R.string.function_on)));
                    ledOn = true;
                } else {
                    mLEDButton.setContentDescription(getString(R.string.led_description, getString(R.string.function_off)));
                    ledOn = false;
                }
                break;
            case R.id.button_proximity_sensor:
                if(!proximityOn) {
                    mProximityButton.setContentDescription(getString(R.string.proximity_description, getString(R.string.function_on)));
                    proximityOn = true;
                } else {
                    mProximityButton.setContentDescription(getString(R.string.proximity_description, getString(R.string.function_off)));
                    proximityOn = false;
                }
                break;
            case R.id.button_text_to_speech:
                if(!beepingOn) {
                    mBeepButton.setContentDescription(getString(R.string.beep_description, getString(R.string.function_on)));
                    beepingOn = true;
                } else {
                    mBeepButton.setContentDescription(getString(R.string.beep_description, getString(R.string.function_off)));
                    beepingOn = false;
                }
                break;
        }
    }

    public void setListener(OnFragmentInteractionListener fragmentInteractionListener) {
        this.mListener = fragmentInteractionListener;
    }

    /**
     * Callback implemented by MainController
     */
    public interface OnFragmentInteractionListener {
        void onFragmentInteraction(int command);
    }
}
