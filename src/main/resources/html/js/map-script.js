
/* --------------------------------------------
Google Map
-------------------------------------------- */ 
window.onload = MapLoadScript;
function GmapInit() {
      Gmap = $('.map-canvas');
      Gmap.each(function() {
        var $this           = $(this),
            lat             = '',
            lng             = '',
            zoom            = 12,
            scrollwheel     = false,
            zoomcontrol     = true,
            draggable       = true,
            mapType         = google.maps.MapTypeId.ROADMAP,
            title           = '',
            contentString   = '',
            theme_icon_path         = $this.data('icon-path'),
            dataLat         = $this.data('lat'),
            dataLng         = $this.data('lng'),
            dataZoom        = $this.data('zoom'),
            dataType        = $this.data('type'),
            dataScrollwheel = $this.data('scrollwheel'),
            dataZoomcontrol = $this.data('zoomcontrol'),
            dataHue         = $this.data('hue'),
            dataTitle       = $this.data('title'),
            dataContent     = $this.data('content');
            
        if( dataZoom !== undefined && dataZoom !== false ) {
            zoom = parseFloat(dataZoom);
        }
        if( dataLat !== undefined && dataLat !== false ) {
            lat = parseFloat(dataLat);
        }
        if( dataLng !== undefined && dataLng !== false ) {
            lng = parseFloat(dataLng);
        }
        if( dataScrollwheel !== undefined && dataScrollwheel !== null ) {
            scrollwheel = dataScrollwheel;
        }
        if( dataZoomcontrol !== undefined && dataZoomcontrol !== null ) {
            zoomcontrol = dataZoomcontrol;
        }
        if( dataType !== undefined && dataType !== false ) {
            if( dataType == 'satellite' ) {
                mapType = google.maps.MapTypeId.SATELLITE;
            } else if( dataType == 'hybrid' ) {
                mapType = google.maps.MapTypeId.HYBRID;
            } else if( dataType == 'terrain' ) {
                mapType = google.maps.MapTypeId.TERRAIN;
            }           
        }
        if( dataTitle !== undefined && dataTitle !== false ) {
            title = dataTitle;
        }
        if( navigator.userAgent.match(/iPad|iPhone|Android/i) ) {
            draggable = false;
        }
        
        var mapOptions = {
          zoom        : zoom,
          scrollwheel : scrollwheel,
          zoomControl : zoomcontrol,
          draggable   : draggable,
          center      : new google.maps.LatLng(lat, lng),
          mapTypeId   : mapType
        };      
        var map = new google.maps.Map($this[0], mapOptions);
        
        //var image = 'images/icons/map-marker.png';
        var image = theme_icon_path;
        
        if( dataContent !== undefined && dataContent !== false ) {
            contentString = '<div class="map-data">' + '<h6>' + title + '</h6>' + '<div class="map-content">' + dataContent + '</div>' + '</div>';
        }
        var infowindow = new google.maps.InfoWindow({
            content: contentString
        });
        
        var marker = new google.maps.Marker({
          position : new google.maps.LatLng(lat, lng),
          map      : map,
          icon     : image,
          title    : title
        });
        if( dataContent !== undefined && dataContent !== false ) {
            google.maps.event.addListener(marker, 'click', function() {
                infowindow.open(map,marker);
            });
        }
        
        if( dataHue !== undefined && dataHue !== false ) {
          var styles = [
    {
        "featureType": "all",
        "elementType": "labels.text.fill",
        "stylers": [
            {
                "saturation": 36
            },
            {
                "color": "#000000"
            },
            {
                "lightness": 40
            }
        ]
    },
    {
        "featureType": "all",
        "elementType": "labels.text.stroke",
        "stylers": [
            {
                "visibility": "on"
            },
            {
                "color": "#000000"
            },
            {
                "lightness": 16
            }
        ]
    },
    {
        "featureType": "all",
        "elementType": "labels.icon",
        "stylers": [
            {
                "visibility": "off"
            }
        ]
    },
    {
        "featureType": "administrative",
        "elementType": "geometry.fill",
        "stylers": [
            {
                "color": "#000000"
            },
            {
                "lightness": 20
            }
        ]
    },
    {
        "featureType": "administrative",
        "elementType": "geometry.stroke",
        "stylers": [
            {
                "color": "#000000"
            },
            {
                "lightness": 17
            },
            {
                "weight": 1.2
            }
        ]
    },
    {
        "featureType": "landscape",
        "elementType": "geometry",
        "stylers": [
            {
                "color": "#000000"
            },
            {
                "lightness": 20
            }
        ]
    },
    {
        "featureType": "poi",
        "elementType": "geometry",
        "stylers": [
            {
                "color": "#000000"
            },
            {
                "lightness": 21
            }
        ]
    },
    {
        "featureType": "road.highway",
        "elementType": "geometry.fill",
        "stylers": [
            {
                "color": "#000000"
            },
            {
                "lightness": 17
            }
        ]
    },
    {
        "featureType": "road.highway",
        "elementType": "geometry.stroke",
        "stylers": [
            {
                "color": "#000000"
            },
            {
                "lightness": 29
            },
            {
                "weight": 0.2
            }
        ]
    },
    {
        "featureType": "road.arterial",
        "elementType": "geometry",
        "stylers": [
            {
                "color": "#000000"
            },
            {
                "lightness": 18
            }
        ]
    },
    {
        "featureType": "road.local",
        "elementType": "geometry",
        "stylers": [
            {
                "color": "#000000"
            },
            {
                "lightness": 16
            }
        ]
    },
    {
        "featureType": "transit",
        "elementType": "geometry",
        "stylers": [
            {
                "color": "#000000"
            },
            {
                "lightness": 19
            }
        ]
    },
    {
        "featureType": "water",
        "elementType": "geometry",
        "stylers": [
            {
                "color": "#000000"
            },
            {
                "lightness": 17
            }
        ]
    }
];
          map.setOptions({styles: styles});
        }
     });
}
    
function MapLoadScript() {
    var script = document.createElement('script');
    script.type = 'text/javascript';
    GmapInit();
    document.body.appendChild(script);
}
