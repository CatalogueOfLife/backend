<!DOCTYPE html>
<html>
<head>
  <title>${d.title}</title>
  <meta charset="UTF-8">
  <meta name="description" content="${d.title!}">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <link rel="stylesheet" href="file:///Users/markus/Desktop/catalogue.css">
  <script src="https://kit.fontawesome.com/9dcb058c00.js" crossorigin="anonymous"></script>
  <script type="text/javascript">

  var img;

  // based on https://www.patrick-wied.at/blog/how-to-create-transparency-in-images-with-html5canvas
  function assignAlpha() {
    // create and customize the canvas
    var canvas = document.createElement("canvas");
    canvas.width = img.width;
    canvas.height = img.height;
    document.body.appendChild(canvas);
    // get the context
    var ctx = canvas.getContext("2d");
    // draw the image into the canvas
    ctx.drawImage(img, 0, 0);
    // get the image data object
    var image = ctx.getImageData(0, 0, 500, 200);
    // get the image data values
    var imageData = image.data,
    length = imageData.length;
    // A pixel in the imagedata is represented by four values: red, green, blue, alpha (rgba) therefore we have to change every fourth value if rbg was white
    // set every fourth value to 50
    for(var i=3; i < length; i+=4){
    if (imageData[i-3] == 255 && imageData[i-2] == 255 && imageData[i-1] == 255) {
      imageData[i] = 50;
    }
    }
    // after the manipulation, reset the data
    image.data = imageData;
    // and put the imagedata back to the canvas
    ctx.putImageData(image, 0, 0);
    //TODO: doesnt seem to work :)
    //img.src = canvas.toDataURL();
  }

  window.onload = function () {
    img = document.getElementById("logo");
    if (img.complete) {
      img.crossOrigin = "Anonymous";
      assignAlpha()
    }
  }
  </script>
</head>
<body>

<header>
  <div class="site-name">
    <!-- ${d.key} -->
    <a href="https://data.catalogue.life/dataset/1199/classification"><img id="logo" src="http://api.catalogue.life/dataset/1199/logo?size=MEDIUM">${d.title!"Catalogue of World Pterophoroidea"}</a>
  </div>
</header>

<#if d.description??>
  <div class="Overview">
    <p>${d.description}</p>
  </div>
</#if>

<#list classification as t>
<div class="HigherTaxon" id="${t.getId()}">
  <div class="Name">${t.name.rank}: <strong>${t.label}</strong>
   <#--
    <div class="Reference">Page reference (Original description): Linnaeus, C. (1758), <a href="https://www.biodiversitylibrary.org/page/726898" target="_blank"><strong>9</strong> <i class="fas fa-external-link-alt fa-sm"></i></a></div>
    -->
  </div>
   <#--
  <div class="References">
    <div class="Reference"><strong>Linnaeus, C. (1758)</strong> Systema naturae per regna tria naturae: secundum classes, ordines, genera, species, cum characteribus, differentiis, synonymis, locis. 1-824. <a href="https://www.biodiversitylibrary.org/page/726886" target="_blank"><i class="fas fa-external-link-alt fa-sm"></i></a></div>
  </div>
    -->
</div>
</#list>