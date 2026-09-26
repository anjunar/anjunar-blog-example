package com.anjunar.blog

import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class GreetingService {

  def message: String = "Welcome to Anjunar Blog Tutorial!\n"

}
