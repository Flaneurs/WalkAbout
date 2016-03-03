package app.flaneurs.com.flaneurs;

import android.app.Application;

import com.parse.Parse;
import com.parse.ParseObject;
import com.parse.interceptors.ParseLogInterceptor;

import app.flaneurs.com.flaneurs.models.Post;
import app.flaneurs.com.flaneurs.models.Comment;

/**
 * Created by kpu on 3/2/16.
 */
public class FlaneurApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        ParseObject.registerSubclass(Post.class);
        ParseObject.registerSubclass(Comment.class);

        // set applicationId and server based on the values in the Heroku settings.
        // any network interceptors must be added with the Configuration Builder given this syntax
        Parse.initialize(new Parse.Configuration.Builder(this)
                .addNetworkInterceptor(new ParseLogInterceptor())
                .server(getString(R.string.parse_server_url)).build());
    }
}