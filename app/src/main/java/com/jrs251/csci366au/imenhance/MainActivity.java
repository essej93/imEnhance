package com.jrs251.csci366au.imenhance;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE = 1;
    private ImageView imageViewLeft, imageViewRight;
    private Bitmap imageBitMap, imageRescaled, imageLuminanceY, imagePowerLaw, greyScale;
    private RadioGroup radioGroup;
    private boolean imageSelected = false, newImageSelected = false, isClearingRadioGroup = false;
    private float [] lowGammaLUT;// < 1 makes darker pixels lighter with less change to lighter pixels
    private float [] highGammaLUT; // > 1 makes lighter pixels darker with less change to darker pixels
    private boolean highIntensity = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // assigns views to variables
        imageViewLeft = findViewById(R.id.imageViewLeft);
        imageViewRight = findViewById(R.id.imageViewRight);
        radioGroup = findViewById(R.id.imageRadioGroup);

        generateLookUpTables();

        // sets radio group listener
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch(checkedId){
                    case R.id.luminanceRadioButton:
                        if(isClearingRadioGroup) break;
                        if(!imageSelected) {
                            Toast.makeText(getApplicationContext(), "No image selected", Toast.LENGTH_SHORT).show();
                            radioGroup.clearCheck();
                            break;
                        }
                        setImageLuminanceY();
                        break;
                    case R.id.powerLawRadioButton:
                        if(isClearingRadioGroup) break;
                        if(!imageSelected) {
                            Toast.makeText(getApplicationContext(), "No image selected", Toast.LENGTH_SHORT).show();
                            radioGroup.clearCheck();
                            break;
                        }
                        setImagePowerLaw();
                        break;
                }
            }
        });
    }

    // method to convert image to grey scale
    private void convertToGreyscale(Bitmap image){

        int width = image.getWidth();
        int height = image.getHeight();

        // Calculate the average intensity of the image
        int[] pixels = new int[width*height];
        image.getPixels(pixels, 0, width, 0, 0, width, height);

        greyScale = image.copy(Bitmap.Config.ARGB_8888, true);

        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int r = Color.red(pixel);
            int g = Color.green(pixel);
            int b = Color.blue(pixel);
            int grey = (int) (0.299 * r + 0.587 * g + 0.114 * b); // calculates luminance/greyscale pixel
            int newPixel = Color.rgb(grey, grey ,grey);

            pixels[i] = newPixel;
        }

        greyScale.setPixels(pixels, 0, width, 0, 0, width, height);

    }

    // method to calculate intensity of image
    // input arg should be an image that has already been converted
    // to greyscale
    private double calculateAvgIntensity(Bitmap image){

        int width = image.getWidth();
        int height = image.getHeight();

        // Calculate the average intensity of the image
        int[] pixels = new int[width*height];
        image.getPixels(pixels, 0, width, 0, 0, width, height);

        double sum = 0;
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int intensity = Color.red(pixel);
            sum += intensity;
        }

        double average = sum / (width * height);

        return average;
    }

    // method to generate look up tables for 2 gamma values
    private void generateLookUpTables(){

        float lowGamma = 0.45f;
        float highGamma = 2.2f;

        int SIZE = 256;

        lowGammaLUT = new float[SIZE];
        highGammaLUT = new float[SIZE];

        for(int i = 0; i < SIZE; i++){
            lowGammaLUT[i] = (float) (255 * Math.pow(i / 255.0, lowGamma));
        }

        for(int i = 0; i < SIZE; i++){
            highGammaLUT[i] = (float) (255 * Math.pow(i / 255.0, highGamma));
        }
    }

    // method for selecting image
    public void selectImage(View view){
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        startActivityForResult(intent,REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        InputStream stream = null;
        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK)
            try {
                // recycle unused bitmaps
                if (imageBitMap != null) {
                    imageBitMap.recycle();
                }
                stream = getContentResolver().openInputStream(data.getData());
                imageBitMap = BitmapFactory.decodeStream(stream);

                //imageViewLeft.setImageBitmap(imageBitMap);
                scaleImage(imageBitMap);

                // recycles bitmap and sets it to null
                if(imagePowerLaw != null){
                    imagePowerLaw.recycle();
                    imagePowerLaw = null;
                }

                // recycles bitmap and sets it to null
                if(imageLuminanceY != null){
                    imageLuminanceY.recycle();
                    imageLuminanceY = null;
                }

                // clears radio group check
                // bool is to stop radio group listener from triggering
                isClearingRadioGroup = true;
                radioGroup.clearCheck();
                isClearingRadioGroup = false;

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } finally {
                if (stream != null)
                    try {
                        stream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
            }
    }

    // scales down image
    private void scaleImage(Bitmap image){

        // divides pixel width/height by 2
        int width, height;
        width = image.getWidth()/2;
        height = image.getHeight()/2;

        // creates new scaled down bitmap and stores it for later use
        imageRescaled = Bitmap.createScaledBitmap(image, width, height, true);

        imageViewLeft.setImageBitmap(imageRescaled);
        imageViewRight.setImageBitmap(imageRescaled);
        imageSelected = true;

        convertToGreyscale(imageRescaled);
        determineIntensity();
    }

    private void determineIntensity(){
        if(calculateAvgIntensity(greyScale) > 127.5) highIntensity = true;
        else highIntensity = false;
    }

    private void setImageLuminanceY(){

        // checks if bitmap already exists
        if(imageLuminanceY != null){
            imageViewRight.setImageBitmap(imageLuminanceY);
            return;
        }

        // copies scaled img to bitmapY
        imageLuminanceY = imageRescaled.copy(Bitmap.Config.ARGB_8888, true);
        int width = imageRescaled.getWidth();
        int height = imageRescaled.getHeight();

        // creates array to hold pixel values uses getPixels to copy values to the array.
        int[] pixels = new int[width*height];
        imageRescaled.getPixels(pixels, 0, width, 0, 0, width, height);

        // for loop to go through each pixel
        int a,r,g,b, pixel, Y, Cb, Cr;
        for(int i = 0; i < pixels.length; i++){
            // gets current pixel then gets ARGB values
            pixel = pixels[i];
            a = Color.alpha(pixel);
            r = Color.red(pixel);
            g = Color.green(pixel);
            b = Color.blue(pixel);

            // calculates the YCBCr values
            Y = (int) (0.299 * r + 0.587 * g + 0.114 * b);
            Cb = (int) (128-0.169 *   r-0.331   * g + 0.500 * b);
            Cr = (int) (128+0.500 *   r - 0.419 * g - 0.081 * b);

            // applies new pixel value only to luminance Y using LUT
            // based on avg intensity
            if(highIntensity){
                Y = (int) highGammaLUT[Y];
            } else Y = (int) lowGammaLUT[Y];

            // calculates new pixel value and assigns it to current pixel
            pixels[i] = Color.argb(a,Y,Cb,Cr);
            //pixels[i] = Color.argb(a, Y, g, b);

        }

        // sets pixels to bitmap then sets the image view
        imageLuminanceY.setPixels(pixels, 0, width, 0, 0, width, height);
        imageViewRight.setImageBitmap(imageLuminanceY);

    }

    // method converts the pixel values of an image using the
    // respective gammaLUT based on the average intensity of the image
    private void setImagePowerLaw(){


        // checks if bitmap already exists
        if(imagePowerLaw != null){
            imageViewRight.setImageBitmap(imagePowerLaw);
            return;
        }

        imagePowerLaw = imageRescaled.copy(Bitmap.Config.ARGB_8888, true);
        int width = imageRescaled.getWidth();
        int height = imageRescaled.getHeight();

        int[] pixels = new int[width*height];
        imageRescaled.getPixels(pixels, 0, width, 0, 0, width, height);

        // for loop to go through each pixel
        int a,r,g,b, pixel;
        for(int i = 0; i < pixels.length; i++){
            // gets current pixel then gets ARGB values
            pixel = pixels[i];
            a = Color.alpha(pixel);
            r = Color.red(pixel);
            g = Color.green(pixel);
            b = Color.blue(pixel);


            // sets the new RGB values using the LUT table based on whether the image is high intensity
            if(highIntensity){
                r = (int) highGammaLUT[r];
                g = (int) highGammaLUT[g];
                b = (int) highGammaLUT[b];
            } else {
                r = (int) lowGammaLUT[r];
                g = (int) lowGammaLUT[g];
                b = (int) lowGammaLUT[b];

            }

            // calculates new pixel value and assigns it to current pixel
            pixels[i] = Color.argb(a,r,g,b);

        }

        // sets pixels to bitmap then sets the image view
        imagePowerLaw.setPixels(pixels, 0, width, 0, 0, width, height);
        imageViewRight.setImageBitmap(imagePowerLaw);


    }


}