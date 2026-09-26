package com.helix.facade.channel;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * Channel gateway client (aligned with all external endpoints of helix-channel).
 *
 * <p>Addressed by service name {@code helix-channel} via nacos service discovery instead of a
 * hard-coded address; on cluster deployment the nacos registry balances the load automatically.</p>
 */
@FeignClient("helix-channel")
public interface ChannelService {

    // ---------- auth (WeChat auth, pure HTTP) ----------

    /** Exchanges a WeChat auth code for the phone number */
    @PostMapping("/auth/phoneNumber")
    AuthPhoneNumberRsp authPhoneNumber(@RequestBody AuthPhoneNumberReq req);

    /** Exchanges a WeChat auth code for the openId */
    @PostMapping("/auth/openId")
    java.util.Map<String, Object> authOpenId(@RequestBody java.util.Map<String, Object> req);

    // ---------- ocr ----------

    /** ID card OCR */
    @PostMapping("/ocr/idCard")
    OcrIdCardRsp createOcrIdCard(@RequestBody OcrIdCardReq req);

    /** Bank card OCR */
    @PostMapping("/ocr/bankCard")
    OcrBankCardRsp createOcrBankCard(@RequestBody OcrBankCardReq req);

    // ---------- sms ----------

    /** Sends an SMS message */
    @PostMapping("/sms/sendMsg")
    SendMsgRsp sendMsg(@RequestBody SendMsgReq req);

    // ---------- helix (credit report) ----------

    /** Pudao credit report query */
    @PostMapping("/helix/query")
    HelixRsp helixQuery(@RequestBody HelixReq req);

    /** Pudao three-element verification */
    @PostMapping("/helix/helixValidThree")
    HelixValidThreeRsp helixValidThree(@RequestBody HelixReq req);

    /** Bank credit info query (used by the helix-feature bank channel) */
    @PostMapping("/helix/pquery")
    BankHelixRsp bankHelixQuery(@RequestBody BankHelixReq req);

    // ---------- baiHang (Baihang Credit) ----------

    /** Baihang Credit query */
    @PostMapping("/baiHang/query")
    BaiHangRsp baiHangQuery(@RequestBody BaiHangReq req);

    // ---------- bank (partner bank) ----------

    /** Loan due bill query */
    @PostMapping("/bank/loanQuery")
    LoanQueryRsp loanQuery(@RequestBody LoanQueryReq req);

    /** Repayment plan query */
    @PostMapping("/bank/payPlanQuery")
    PayPlanQueryRsp payPlanQuery(@RequestBody PayPlanQueryReq req);

    /** Account info query */
    @PostMapping("/bank/acctInfo")
    AcctInfoRsp getAcctInfo(@RequestBody AcctInfoReq req);

    // ---------- search ----------

    /** Fuzzy search by company name */
    @PostMapping("/search/company")
    List<String> companySearch(@RequestBody String word);

    /** Reverse lookup of administrative region by latitude/longitude */
    @PostMapping("/search/locationInfo")
    LocationInfoRsp locationInfo(@RequestBody LocationInfoReq req);

    // ---------- sign (e-signature) ----------

    /** Creates a signing account */
    @PostMapping("/sign/createAccount")
    CreateAccountRsp createAccount(@RequestBody CreateAccountReq req);

    /** Queries the signing account */
    @PostMapping("/sign/queryAccount")
    QueryAccountRsp queryAccount(@RequestBody QueryAccountReq req);

    /** Updates the signing account */
    @PostMapping("/sign/updateAccount")
    UpdateAccountRsp updateAccount(@RequestBody UpdateAccountReq req);

    /** Closes the signing account */
    @PostMapping("/sign/delAccount")
    DeleteAccountRsp delAccount(@RequestBody DeleteAccountReq req);

    /** Creates a personal seal */
    @PostMapping("/sign/createPersonSeal")
    CreatePersonalSealRsp createPersonSeal(@RequestBody CreatePersonalSealReq req);

    /** Sends the signing verification code */
    @PostMapping("/sign/sendVerifyCode")
    SendVerifyCodeRsp sendVerifyCode(@RequestBody SendVerifyCodeReq req);

    /** Verifies the signing verification code */
    @PostMapping("/sign/verifyCode")
    VerifyCodeRsp verifyCode(@RequestBody VerifyCodeReq req);
}
