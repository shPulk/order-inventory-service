package com.acme.oims.order;

public class IllegalTransitionException extends RuntimeException{

    public IllegalTransitionException(OrderStatus from, OrderStatus to) {
        super("Illegal order transition: "+ from + "->" + to);
    }
}

