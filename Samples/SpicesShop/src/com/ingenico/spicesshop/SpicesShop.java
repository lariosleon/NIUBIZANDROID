package com.ingenico.spicesshop;

import java.util.ArrayList;
import android.app.Application;
import android.content.Context;
import androidx.multidex.MultiDex;

public class SpicesShop extends Application {

	public static ArrayList<struct_spice> monPanier = new ArrayList<struct_spice>();
    
	public float getTotalPrice(){
		float totalPrice = 0;
		for (int liste = 0; liste < monPanier.size(); liste++)
        {
			totalPrice += (monPanier.get(liste).price)*(monPanier.get(liste).quantity);

        }
	    return totalPrice;
	}
	
	public void addSpice(struct_spice st_spice){
		boolean spicefound = false;
		for (int liste = 0; liste < monPanier.size(); liste++)
        {
			if (monPanier.get(liste).name.equals(st_spice.name))
			{
				monPanier.get(liste).quantity += st_spice.quantity;
				spicefound = true;
				break;
			}
        }
		if (spicefound == false)
		{
			monPanier.add(st_spice);
		}
	}

	@Override
	protected void attachBaseContext(Context base) {
		super.attachBaseContext(base);
		MultiDex.install(this);
	}

}
