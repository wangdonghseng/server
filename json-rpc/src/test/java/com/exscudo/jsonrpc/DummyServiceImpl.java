package com.exscudo.jsonrpc;

class DummyServiceImpl {

    public boolean method(int arg0, String arg1) {
        return true;
    }

    public void method1(int arg0, String arg1) throws JsonException {
        throw new JsonException("args1");
    }

    public void method2(int arg0, String arg1) throws Exception {
        throw new Exception("some exception.");
    }
}
