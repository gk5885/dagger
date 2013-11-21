package coffee;

import javax.inject.Inject;

public class CoffeeApp implements Runnable {
  private final CoffeeMaker coffeeMaker;

  @Inject CoffeeApp(CoffeeMaker coffeeMaker) {
    this.coffeeMaker = coffeeMaker;
  }

  @Override public void run() {
    coffeeMaker.brew();
  }

  public static void main(String[] args) {
    CoffeeAppComponent coffeeAppComponent = new DaggerComponent_CoffeeAppComponent();
    CoffeeApp coffeeApp = coffeeAppComponent.getCoffeeApp();
    coffeeApp.run();
  }
}
