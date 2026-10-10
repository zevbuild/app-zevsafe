package android.net;

import java.util.Collections;
import java.util.List;

public class TestUri extends Uri {
    public TestUri() {
        super();
    }

    @Override
    public boolean isHierarchical() {
        return false;
    }

    @Override
    public boolean isRelative() {
        return false;
    }

    @Override
    public String getScheme() {
        return "test";
    }

    @Override
    public String getSchemeSpecificPart() {
        return "test";
    }

    @Override
    public String getEncodedSchemeSpecificPart() {
        return "test";
    }

    @Override
    public String getAuthority() {
        return "test";
    }

    @Override
    public String getEncodedAuthority() {
        return "test";
    }

    @Override
    public String getUserInfo() {
        return null;
    }

    @Override
    public String getEncodedUserInfo() {
        return null;
    }

    @Override
    public String getHost() {
        return "test";
    }

    @Override
    public int getPort() {
        return -1;
    }

    @Override
    public String getPath() {
        return "";
    }

    @Override
    public String getEncodedPath() {
        return "";
    }

    @Override
    public String getQuery() {
        return null;
    }

    @Override
    public String getEncodedQuery() {
        return null;
    }

    @Override
    public String getFragment() {
        return null;
    }

    @Override
    public String getEncodedFragment() {
        return null;
    }

    @Override
    public List<String> getPathSegments() {
        return Collections.emptyList();
    }

    @Override
    public String getLastPathSegment() {
        return "";
    }

    @Override
    public Builder buildUpon() {
        return null;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(android.os.Parcel dest, int flags) {}

    @Override
    public String toString() {
        return "test://";
    }
}
