package me.chiontang.wechatmomentexport.models;

import java.util.List;

/**
 * Created by Administrator on 2016/11/4.
 */

public class SnsBean {

    /**
     * snsId : 12399684823877300516
     * authorName : 风
     * authorId : wxid_prgeiu02ctmv22
     * content :
     * comments : []
     * likes : []
     * mediaList : ["http://vweixinf.tc.qq.com/102/20202/snsvideodownload?filekey=30270201010420301e02016604025348041048de8443f4a217744f129035f1e3a1960203031fa40400&bizid=1023&hy=SH&fileparam=302c020101042530230204940a37150204581ae53102024eea02031e8d7e02030f424002047c9c360a0201000400"]
     * rawXML : <TimelineObject><id><![CDATA[12399684823877300516]]></id><username><![CDATA[wxid_prgeiu02ctmv22]]></username><createTime><![CDATA[1478157618]]></createTime><contentDescShowType>0</contentDescShowType><contentDescScene>0</contentDescScene><private><![CDATA[0]]></private><contentDesc></contentDesc><contentattr><![CDATA[0]]></contentattr><sourceUserName></sourceUserName><sourceNickName></sourceNickName><statisticsData></statisticsData><location poiClickableStatus =  "0"  poiClassifyId =  ""  poiScale =  "0"  longitude =  "0.0"  city =  ""  poiName =  ""  latitude =  "0.0"  poiClassifyType =  "0"  poiAddress =  "" ></location><ContentObject><contentStyle><![CDATA[15]]></contentStyle><title><![CDATA[微信小视频]]></title><description></description><contentUrl><![CDATA[https://support.weixin.qq.com/cgi-bin/mmsupport-bin/readtemplate?t=page/common_page__upgrade&v=1]]></contentUrl><mediaList><media><id><![CDATA[12399684824253346049]]></id><type><![CDATA[6]]></type><title></title><description></description><private><![CDATA[0]]></private><url type =  "1" ><![CDATA[http://vweixinf.tc.qq.com/102/20202/snsvideodownload?filekey=30270201010420301e02016604025348041048de8443f4a217744f129035f1e3a1960203031fa40400&bizid=1023&hy=SH&fileparam=302c020101042530230204940a37150204581ae53102024eea02031e8d7e02030f424002047c9c360a0201000400]]></url><thumb type =  "1" ><![CDATA[http://vweixinthumb.tc.qq.com/150/20250/snsvideodownload?filekey=30270201010420301e0202009604025348041022a0fddb118868633201c2d3cffa704302023bf50400&bizid=1023&hy=SH&fileparam=302c020101042530230204940a37150204581ae53102024f1a02031e8d7e02030f424002047c9c360a0201000400]]></thumb><size></size></media></mediaList></ContentObject><actionInfo></actionInfo></TimelineObject>
     * timestamp : 1478157618
     */

    private String snsId;
    private String authorName;
    private String authorId;
    private String content;
    private String rawXML;
    private int timestamp;
    private List<?> comments;
    private List<?> likes;
    private List<String> mediaList;

    public String getSnsId() {
        return snsId;
    }

    public void setSnsId(String snsId) {
        this.snsId = snsId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getAuthorId() {
        return authorId;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getRawXML() {
        return rawXML;
    }

    public void setRawXML(String rawXML) {
        this.rawXML = rawXML;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    public List<?> getComments() {
        return comments;
    }

    public void setComments(List<?> comments) {
        this.comments = comments;
    }

    public List<?> getLikes() {
        return likes;
    }

    public void setLikes(List<?> likes) {
        this.likes = likes;
    }

    public List<String> getMediaList() {
        return mediaList;
    }

    public void setMediaList(List<String> mediaList) {
        this.mediaList = mediaList;
    }
}
