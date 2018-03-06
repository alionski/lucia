package se.mah.sensorteknik.aliona.lucia;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;


/**
 * Fragment containing a simple layout with information about how to use the application.
 */
public class InformationFragment extends Fragment {

    /**
     * Required empty constructor.
     */
    public InformationFragment() {}

    /**
     * To retrieve a new instance of the fragment.
     * @return InformationFragment to display
     */
    public static InformationFragment newInstance() {
        return new InformationFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_information, container, false);
        return view;
    }

}
