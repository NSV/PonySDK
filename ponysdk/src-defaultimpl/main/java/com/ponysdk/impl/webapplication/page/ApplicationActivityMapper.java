
package com.ponysdk.impl.webapplication.page;

import org.springframework.beans.factory.annotation.Autowired;

import com.ponysdk.core.activity.Activity;
import com.ponysdk.core.activity.ActivityMapper;
import com.ponysdk.core.place.Place;
import com.ponysdk.impl.webapplication.application.ApplicationActivity;
import com.ponysdk.impl.webapplication.page.place.LoginPlace;

public class ApplicationActivityMapper implements ActivityMapper {

    @Autowired
    private Activity loginActivity;

    @Autowired
    private ApplicationActivity applicationActivity;

    @Override
    public Activity getActivity(final Place place) {
        if (place instanceof LoginPlace) return loginActivity;
        else return applicationActivity;
    }

}
